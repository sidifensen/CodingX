package com.codingx.chat.application.service;

/**
 * 队列运行态视图。
 * @param mode 门控模式。
 * @param maxConcurrent 最大并发数。
 * @param activeCount 活跃执行数。
 * @param waitingCount 排队数。
 * @param availablePermits 可用许可数。
 */
public record ChatRuntimeQueueDashboardView(
    String mode,
    int maxConcurrent,
    int activeCount,
    int waitingCount,
    int availablePermits
) {
}
