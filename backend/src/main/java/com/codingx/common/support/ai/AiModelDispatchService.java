package com.codingx.common.support.ai;

import cn.hutool.core.collection.CollUtil;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.config.AiProperties;
import com.codingx.config.DynamicAiProperties;
import com.codingx.config.DynamicAiRoutingProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
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
        this(providerClients, aiProperties, null);
    }

    /**
     * 使用 AI 配置与动态路由配置装配路由层依赖。
     * @param providerClients provider 客户端列表。
     * @param aiProperties AI 配置。
     * @param dynamicProperties 动态路由配置。
     */
    public AiModelDispatchService(
        List<AiProviderClient> providerClients,
        AiProperties aiProperties,
        DynamicAiRoutingProperties dynamicProperties
    ) {
        this(providerClients, aiProperties, dynamicProperties, null);
    }

    /**
     * 使用 AI 配置、动态路由配置和动态 provider 配置装配路由层依赖。
     * @param providerClients provider 客户端列表。
     * @param aiProperties AI 配置。
     * @param dynamicProperties 动态路由配置。
     * @param dynamicAiProperties 动态 AI 配置。
     */
    public AiModelDispatchService(
        List<AiProviderClient> providerClients,
        AiProperties aiProperties,
        DynamicAiRoutingProperties dynamicProperties,
        DynamicAiProperties dynamicAiProperties
    ) {
        this(
            providerClients,
            new AiProviderHealthRegistry(
                dynamicProperties == null
                    ? aiProperties.getSelection().getFailureThreshold()
                    : dynamicProperties.failureThreshold(),
                dynamicProperties == null
                    ? aiProperties.getSelection().getOpenDurationMs()
                    : dynamicProperties.openDurationMs()
            ),
            new AiModelSelector(aiProperties, dynamicProperties, dynamicAiProperties)
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
        this.providerClients = new LinkedHashMap<>();
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
        List<AiModelTarget> targets = aiModelSelector.selectChatCandidates(
            request.preferredModel(),
            request.thinkingEnabled(),
            request.attachments()
        );
        for (AiModelTarget target : targets) {
            String modelId = target.id();
            if (!healthRegistry.allowCall(modelId)) {
                continue;
            }
            AiProviderClient providerClient = resolveProviderClient(target.candidate().getProvider());
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
                lastError = new IllegalStateException(
                    providerClient.provider() + "/" + target.candidate().getModel()
                        + ErrorMessageCatalog.AI_STREAM_SESSION_NULL_SUFFIX
                );
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
                    throw new IllegalStateException(ErrorMessageCatalog.AI_STREAM_FAILED_AFTER_FIRST_TOKEN, cause);
                }
                healthRegistry.markSuccess(modelId);
                return;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                session.cancel();
                throw new IllegalStateException(ErrorMessageCatalog.AI_ROUTING_INTERRUPTED, exception);
            }
        }
        IllegalStateException exception = new IllegalStateException(ErrorMessageCatalog.AI_NO_AVAILABLE_PROVIDER);
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
                ? new IllegalStateException(
                    provider + "/" + model + ErrorMessageCatalog.AI_STREAM_FAILED_BEFORE_FIRST_TOKEN_SUFFIX
                )
                : result.getError();
            case TIMEOUT -> new IllegalStateException(
                provider + "/" + model + ErrorMessageCatalog.AI_STREAM_TIMEOUT_BEFORE_FIRST_TOKEN_SUFFIX
            );
            case NO_CONTENT -> new IllegalStateException(
                provider + "/" + model + ErrorMessageCatalog.AI_STREAM_COMPLETED_WITHOUT_CONTENT_SUFFIX
            );
            case SUCCESS -> null;
        };
    }

    /**
     * 解析当前模型应使用的 provider 客户端，优先精确匹配，其次回退到兼容客户端。
     * @param providerName provider 名称。
     * @return 命中的 provider 客户端。
     */
    private AiProviderClient resolveProviderClient(String providerName) {
        AiProviderClient exactMatch = providerClients.get(providerName);
        if (exactMatch != null) {
            return exactMatch;
        }
        for (Map.Entry<String, AiProviderClient> entry : providerClients.entrySet()) {
            if (providerName != null && providerName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return providerClients.get("openai-compatible");
    }
}

