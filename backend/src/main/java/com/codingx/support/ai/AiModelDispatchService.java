package com.codingx.support.ai;

import cn.hutool.core.collection.CollUtil;
import com.codingx.config.AiProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 负责按模型候选顺序调度 provider，并在首包前失败时自动切换到下一个候选。
 */
public class AiModelDispatchService {

    private final Map<String, AiProviderClient> providerClients;
    private final AiProviderHealthRegistry healthRegistry;
    private final AiModelSelector aiModelSelector;
    private final List<String> lastAttemptedProviders = new ArrayList<>();

    /**
     * 使用 AI 配置装配路由层默认依赖。
     * @param providerClients provider 客户端列表。
     * @param aiProperties AI 配置。
     */
    public AiModelDispatchService(List<AiProviderClient> providerClients, AiProperties aiProperties) {
        this(
            providerClients,
            new AiProviderHealthRegistry(
                aiProperties.getSelection().getFailureThreshold(),
                aiProperties.getSelection().getOpenDurationMs()
            ),
            new AiModelSelector(aiProperties)
        );
    }

    /**
     * 使用显式健康注册表与选择器构造路由服务，便于测试替换实现。
     * @param providerClients provider 客户端列表。
     * @param healthRegistry 健康注册表。
     * @param aiModelSelector 模型选择器。
     */
    public AiModelDispatchService(
        List<AiProviderClient> providerClients,
        AiProviderHealthRegistry healthRegistry,
        AiModelSelector aiModelSelector
    ) {
        this.providerClients = new ConcurrentHashMap<>();
        for (AiProviderClient providerClient : CollUtil.emptyIfNull(providerClients)) {
            this.providerClients.put(providerClient.provider(), providerClient);
        }
        this.healthRegistry = healthRegistry;
        this.aiModelSelector = aiModelSelector;
    }

    /**
     * 执行流式对话请求，并在首包前失败时自动 fallback。
     * @param request 统一请求对象。
     * @param handler 下游流式处理器。
     */
    public void streamChat(AiConversationRequest request, AiStreamHandler handler) {
        lastAttemptedProviders.clear();
        Throwable lastError = null;
        List<AiModelTarget> targets = aiModelSelector.selectChatCandidates(request.preferredModel(), request.thinkingEnabled());
        for (AiModelTarget target : targets) {
            String modelId = target.id();
            if (!healthRegistry.allowCall(modelId)) {
                continue;
            }
            AiProviderClient providerClient = providerClients.get(target.candidate().getProvider());
            if (providerClient == null) {
                continue;
            }
            lastAttemptedProviders.add(providerClient.provider());
            FirstTokenAwaiter awaiter = new FirstTokenAwaiter();
            FirstTokenBufferingHandler bufferingHandler = new FirstTokenBufferingHandler(handler, awaiter);
            AiStreamSession session;
            try {
                session = providerClient.streamChat(request, target, bufferingHandler);
            } catch (Exception exception) {
                healthRegistry.markFailure(modelId);
                lastError = exception;
                continue;
            }
            if (session == null) {
                healthRegistry.markFailure(modelId);
                lastError = new IllegalStateException(providerClient.provider() + "/" + target.candidate().getModel() + " returned null stream session");
                continue;
            }

            try {
                FirstTokenAwaiter.Result result = awaiter.await(aiModelSelector.firstPacketTimeoutMs(), TimeUnit.MILLISECONDS);
                if (!result.isSuccess()) {
                    healthRegistry.markFailure(modelId);
                    session.cancel();
                    lastError = errorForResult(result, providerClient.provider(), target.candidate().getModel());
                    continue;
                }

                handler.onMetadata(providerClient.provider(), target.candidate().getModel());
                bufferingHandler.commit();
                try {
                    session.completion().join();
                } catch (CompletionException completionException) {
                    healthRegistry.markFailure(modelId);
                    Throwable cause = completionException.getCause() == null ? completionException : completionException.getCause();
                    throw new IllegalStateException("AI stream failed after first token", cause);
                }
                healthRegistry.markSuccess(modelId);
                return;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                session.cancel();
                throw new IllegalStateException("AI routing interrupted", exception);
            }
        }
        IllegalStateException exception = new IllegalStateException("No available AI provider could complete the request");
        if (lastError != null) {
            exception.initCause(lastError);
        }
        handler.onError(exception);
        throw exception;
    }

    /**
     * 暴露最近一次调用的 provider 尝试顺序，供测试和问题定位使用。
     * @return provider 顺序列表。
     */
    public List<String> getLastAttemptedProviders() {
        return List.copyOf(lastAttemptedProviders);
    }

    /**
     * 将首包等待结果映射为可观察的失败原因。
     * @param result 首包等待结果。
     * @param provider provider 名称。
     * @param model 模型名称。
     * @return 失败原因。
     */
    private Throwable errorForResult(FirstTokenAwaiter.Result result, String provider, String model) {
        return switch (result.getType()) {
            case ERROR -> result.getError() == null
                ? new IllegalStateException(provider + "/" + model + " failed before first token")
                : result.getError();
            case TIMEOUT -> new IllegalStateException(provider + "/" + model + " timed out before first token");
            case NO_CONTENT -> new IllegalStateException(provider + "/" + model + " completed without content");
            case SUCCESS -> null;
        };
    }
}
