package com.codingx.support.ai;

import cn.hutool.core.collection.CollUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 负责按候选优先级选择模型 provider，并在失败时执行顺序 fallback。
 */
public class AiModelDispatchService {

    private final List<AiProviderClient> providerClients;
    private final AiProviderHealthRegistry healthRegistry;
    private final List<String> lastAttemptedProviders = new ArrayList<>();

    /**
     * 使用可注入的 provider 列表构建路由服务。
     * @param providerClients provider 列表。
     */
    public AiModelDispatchService(List<AiProviderClient> providerClients) {
        this(providerClients, new AiProviderHealthRegistry(2));
    }

    /**
     * 使用给定 provider 列表和健康状态注册表构建路由服务。
     * @param providerClients provider 列表。
     * @param healthRegistry 健康状态注册表。
     */
    public AiModelDispatchService(List<AiProviderClient> providerClients, AiProviderHealthRegistry healthRegistry) {
        this.providerClients = CollUtil.emptyIfNull(providerClients);
        this.healthRegistry = healthRegistry;
    }

    /**
     * 按候选顺序执行流式调用，并在失败时自动回退。
     * @param request 统一请求对象。
     * @param handler 流式回调。
     */
    public void streamChat(AiConversationRequest request, AiStreamHandler handler) {
        lastAttemptedProviders.clear();
        Throwable lastError = null;
        for (AiProviderClient providerClient : sortedSupportedProviders(request)) {
            String providerName = providerClient.candidate().providerName();
            lastAttemptedProviders.add(providerName);
            try {
                providerClient.streamChat(request, handler);
                healthRegistry.markSuccess(providerName);
                return;
            } catch (Exception exception) {
                healthRegistry.markFailure(providerName);
                lastError = exception;
            }
        }
        IllegalStateException exception = new IllegalStateException("No available AI provider could complete the request");
        if (lastError != null) {
            exception.initCause(lastError);
        }
        throw exception;
    }

    /**
     * 暴露最近一次调用的 provider 尝试顺序，供测试与调试使用。
     * @return provider 名称列表。
     */
    public List<String> getLastAttemptedProviders() {
        return List.copyOf(lastAttemptedProviders);
    }

    /**
     * 按健康状态和优先级筛选候选 provider。
     * @param request 统一请求对象。
     * @return 可执行 provider 列表。
     */
    private List<AiProviderClient> sortedSupportedProviders(AiConversationRequest request) {
        return providerClients.stream()
            .filter(providerClient -> providerClient.supports(request))
            .filter(providerClient -> healthRegistry.isAvailable(providerClient.candidate().providerName()))
            .sorted(Comparator
                .comparing((AiProviderClient providerClient) -> !providerClient.candidate().healthy())
                .thenComparing(providerClient -> -providerClient.candidate().priority()))
            .toList();
    }
}
