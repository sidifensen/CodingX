package com.codingx.chat.application.service;

/**
 * 线程池运行态视图。
 * @param streamActiveCount 聊天入口线程池活跃线程数。
 * @param streamPoolSize 聊天入口线程池当前线程数。
 * @param streamQueueSize 聊天入口线程池排队任务数。
 * @param streamQueueRemainingCapacity 聊天入口线程池队列剩余容量。
 * @param searchActiveCount 搜索线程池活跃线程数。
 * @param searchPoolSize 搜索线程池当前线程数。
 * @param searchQueueSize 搜索线程池排队任务数。
 * @param searchQueueRemainingCapacity 搜索线程池队列剩余容量。
 */
public record ChatRuntimeExecutorDashboardView(
    int streamActiveCount,
    int streamPoolSize,
    int streamQueueSize,
    int streamQueueRemainingCapacity,
    int searchActiveCount,
    int searchPoolSize,
    int searchQueueSize,
    int searchQueueRemainingCapacity
) {
}
