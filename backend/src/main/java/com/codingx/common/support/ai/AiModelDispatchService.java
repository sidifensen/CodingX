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
import java.util.concurrent.TimeUnit;

/**
 * 负责按模型候选顺序调度 provider，并在首包前失败时自动切换到下一个候选。
 */
public class AiModelDispatchService {

    /**
     * Provider 客户端映射，按 provider 编码定位具体模型调用实现。
     */
    private final Map<String, AiProviderClient> providerClients;

    /**
     * Provider 健康注册表，用于记录模型失败并控制熔断窗口。
     */
    private final AiProviderHealthRegistry healthRegistry;

    /**
     * 模型选择器，用于按首选模型、思考模式和附件能力生成候选顺序。
     */
    private final AiModelSelector aiModelSelector;

    /**
     * 最近一次调度尝试过的候选 provider 列表，用于跨线程测试和问题定位。
     */
    private volatile List<String> lastAttemptedProviders = List.of();

    /**
     * 当前线程本次调度尝试过的候选 provider 列表，避免并发请求互相覆盖观测结果。
     */
    private final ThreadLocal<List<String>> currentThreadAttemptedProviders = ThreadLocal.withInitial(List::of);

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
        // 步骤 1：根据动态路由配置或静态选择配置构造健康注册表。
        // 步骤 2：模型选择器同时持有静态和动态 AI 配置，保证候选池可运行时更新。
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
        // 步骤 1：将 provider 客户端按 provider 编码放入有序映射，保留配置注入顺序。
        this.providerClients = new LinkedHashMap<>();
        for (AiProviderClient providerClient : CollUtil.emptyIfNull(providerClients)) {
            this.providerClients.put(providerClient.provider(), providerClient);
        }
        // 步骤 2：健康注册表和模型选择器可由测试显式注入，便于覆盖 fallback 场景。
        this.healthRegistry = healthRegistry;
        this.aiModelSelector = aiModelSelector;
    }

    /**
     * 执行流式对话请求，并在首包前失败时自动 fallback。
     * @param request 统一请求对象。
     * @param handler 下游流式处理器。
     */
    public void streamChat(AiConversationRequest request, AiStreamHandler handler) {
        // 步骤 1：初始化本次请求的 provider 尝试快照，避免复用上一次调用结果。
        List<String> attemptedProviders = new ArrayList<>();
        publishAttemptSnapshot(attemptedProviders);
        Throwable lastError = null;
        // 步骤 2：按首选模型、思考模式和附件能力生成本次有序模型候选。
        List<AiModelTarget> targets = aiModelSelector.selectChatCandidates(
            request.preferredModel(),
            request.thinkingEnabled(),
            request.attachments()
        );
        for (AiModelTarget target : targets) {
            String modelId = target.id();
            String logicalProvider = target.candidate().getProvider();
            // 步骤 3：熔断中的模型直接跳过，避免请求继续打到短期不可用 provider。
            if (!healthRegistry.allowCall(modelId)) {
                continue;
            }
            AiProviderClient providerClient = resolveProviderClient(logicalProvider);
            if (providerClient == null) {
                continue;
            }
            // 步骤 4：记录候选池中的真实 provider；客户端 provider 只表示内部协议适配器。
            attemptedProviders.add(logicalProvider);
            publishAttemptSnapshot(attemptedProviders);
            FirstTokenAwaiter awaiter = new FirstTokenAwaiter();
            FirstTokenBufferingHandler bufferingHandler = new FirstTokenBufferingHandler(handler, awaiter);
            AiStreamHandler providerHandler = guardThinkingEvents(request, bufferingHandler);
            AiStreamSession session;
            try {
                // 步骤 5：启动 provider 流式请求；启动阶段异常表示首包前失败，可尝试下一个候选。
                session = providerClient.streamChat(request, target, providerHandler);
            } catch (Exception exception) {
                healthRegistry.markFailure(modelId);
                lastError = exception;
                continue;
            }
            if (session == null) {
                // 步骤 6：provider 返回空 session 视为首包前失败，标记失败后继续 fallback。
                healthRegistry.markFailure(modelId);
                lastError = new IllegalStateException(
                    logicalProvider + "/" + target.candidate().getModel()
                        + ErrorMessageCatalog.AI_STREAM_SESSION_NULL_SUFFIX
                );
                continue;
            }

            try {
                // 步骤 7：等待首包结果，超时、错误或无内容都取消当前 session 并尝试下一个候选。
                FirstTokenAwaiter.Result result = awaiter.await(aiModelSelector.firstPacketTimeoutMs(), TimeUnit.MILLISECONDS);
                if (!result.isSuccess()) {
                    healthRegistry.markFailure(modelId);
                    session.cancel();
                    lastError = errorForResult(result, logicalProvider, target.candidate().getModel());
                    continue;
                }

                // 步骤 8：首包成功后再向下游提交缓存事件，避免失败候选污染最终响应。
                handler.onMetadata(logicalProvider, target.candidate().getModel());
                bufferingHandler.commit();
                try {
                    // 步骤 9：首包后异常说明响应已开始，不能再 fallback，转换为可观察错误。
                    session.completion().join();
                } catch (CompletionException completionException) {
                    healthRegistry.markFailure(modelId);
                    Throwable cause = completionException.getCause() == null ? completionException : completionException.getCause();
                    publishAttemptSnapshot(attemptedProviders);
                    throw new IllegalStateException(ErrorMessageCatalog.AI_STREAM_FAILED_AFTER_FIRST_TOKEN, cause);
                }
                // 步骤 10：完整流式会话成功后标记健康并结束路由。
                healthRegistry.markSuccess(modelId);
                publishAttemptSnapshot(attemptedProviders);
                return;
            } catch (InterruptedException exception) {
                // 步骤 11：线程中断时取消当前 session 并恢复中断标记。
                Thread.currentThread().interrupt();
                session.cancel();
                publishAttemptSnapshot(attemptedProviders);
                throw new IllegalStateException(ErrorMessageCatalog.AI_ROUTING_INTERRUPTED, exception);
            }
        }
        // 步骤 12：所有候选都不可用时通知 handler 并抛出统一无可用 provider 错误。
        publishAttemptSnapshot(attemptedProviders);
        IllegalStateException exception = new IllegalStateException(ErrorMessageCatalog.AI_NO_AVAILABLE_PROVIDER);
        if (lastError != null) {
            exception.initCause(lastError);
        }
        handler.onError(exception);
        throw exception;
    }

    /**
     * 在调度层统一守住 thinking 展示边界，防止新 provider 或第三方 provider 误发 reasoning 事件。
     * 该包装位于首包缓冲器之前，普通请求中的 thinking 不会被误判为可见首包。
     * @param request 本次 AI 请求。
     * @param delegate 原始下游处理器。
     * @return 已按 deep thinking 开关过滤的处理器。
     */
    private AiStreamHandler guardThinkingEvents(AiConversationRequest request, AiStreamHandler delegate) {
        if (request.thinkingEnabled()) {
            return delegate;
        }
        return new AiStreamHandler() {
            @Override
            public void onMetadata(String provider, String model) {
                delegate.onMetadata(provider, model);
            }

            @Override
            public void onThinkingDelta(String delta) {
                // 普通请求只丢弃 thinking 事件，不影响正文、工具调用、元信息和错误流转。
            }

            @Override
            public void onContentDelta(String delta) {
                delegate.onContentDelta(delta);
            }

            @Override
            public void onToolCall(AiToolCall toolCall) {
                delegate.onToolCall(toolCall);
            }

            @Override
            public void onToolCallDelta(AiToolCallDelta toolCallDelta) {
                delegate.onToolCallDelta(toolCallDelta);
            }

            @Override
            public void onComplete() {
                delegate.onComplete();
            }

            @Override
            public void onError(Throwable throwable) {
                delegate.onError(throwable);
            }
        };
    }

    /**
     * 暴露最近一次调用的 provider 尝试顺序，供测试和问题定位使用。
     * @return provider 顺序列表。
     */
    public List<String> getLastAttemptedProviders() {
        List<String> currentThreadSnapshot = currentThreadAttemptedProviders.get();
        if (!currentThreadSnapshot.isEmpty()) {
            return List.copyOf(currentThreadSnapshot);
        }
        return List.copyOf(lastAttemptedProviders);
    }

    /**
     * 发布本次调度的尝试顺序快照；线程局部快照服务当前调用方，全局快照服务跨线程观测。
     * @param attemptedProviders 当前请求已尝试 provider。
     */
    private void publishAttemptSnapshot(List<String> attemptedProviders) {
        List<String> snapshot = List.copyOf(attemptedProviders);
        currentThreadAttemptedProviders.set(snapshot);
        lastAttemptedProviders = snapshot;
    }

    /**
     * 将首包等待结果映射为可观察的失败原因。
     * @param result 首包等待结果。
     * @param provider provider 名称。
     * @param model 模型名称。
     * @return 失败原因。
     */
    private Throwable errorForResult(FirstTokenAwaiter.Result result, String provider, String model) {
        // 步骤 1：把首包等待结果转换成带 provider/model 上下文的异常，方便日志定位。
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
        // 步骤 1：优先按 provider 编码精确匹配客户端。
        AiProviderClient exactMatch = providerClients.get(providerName);
        if (exactMatch != null) {
            return exactMatch;
        }
        // 步骤 2：providerName 带后缀时允许前缀匹配，兼容同类 provider 的扩展编码。
        for (Map.Entry<String, AiProviderClient> entry : providerClients.entrySet()) {
            if (providerName != null && providerName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        // 步骤 3：最终回退通用 OpenAI-compatible 客户端，兼容动态 provider 配置。
        return providerClients.get("openai-compatible");
    }
}

