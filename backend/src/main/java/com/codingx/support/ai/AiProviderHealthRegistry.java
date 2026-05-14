package com.codingx.support.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护 provider 的健康状态与简单熔断计数，供路由层跳过持续失败的候选。
 */
public class AiProviderHealthRegistry {

    private final int failureThreshold;
    private final Map<String, Integer> failureCounts = new ConcurrentHashMap<>();

    /**
     * 使用给定阈值初始化健康状态登记器。
     * @param failureThreshold 连续失败阈值。
     */
    public AiProviderHealthRegistry(int failureThreshold) {
        this.failureThreshold = Math.max(1, failureThreshold);
    }

    /**
     * 标记一次 provider 成功，成功后立即恢复健康状态。
     * @param providerName provider 名称。
     */
    public void markSuccess(String providerName) {
        failureCounts.put(providerName, 0);
    }

    /**
     * 标记一次 provider 失败，并累积熔断计数。
     * @param providerName provider 名称。
     */
    public void markFailure(String providerName) {
        failureCounts.merge(providerName, 1, Integer::sum);
    }

    /**
     * 判断 provider 当前是否可继续参与路由。
     * @param providerName provider 名称。
     * @return 是否可用。
     */
    public boolean isAvailable(String providerName) {
        return failureCount(providerName) < failureThreshold;
    }

    /**
     * 返回当前累计失败次数，供调试与测试使用。
     * @param providerName provider 名称。
     * @return 失败次数。
     */
    public int failureCount(String providerName) {
        return failureCounts.getOrDefault(providerName, 0);
    }
}
