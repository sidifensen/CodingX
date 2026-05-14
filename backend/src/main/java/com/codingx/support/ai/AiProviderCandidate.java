package com.codingx.support.ai;

/**
 * 描述单个模型候选的静态信息，供路由层排序与回退使用。
 */
public record AiProviderCandidate(
    String providerName,
    String modelName,
    int priority,
    boolean healthy
) {
}
