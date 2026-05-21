package com.codingx.chat.infrastructure.runtime;

/**
 * 聊天队列运行态快照，供管理端展示并发、排队与可用许可。
 * @param mode 门控模式 memory 或 redis。
 * @param maxConcurrent 最大并发数。
 * @param activeCount 当前执行中会话数。
 * @param waitingCount 当前排队会话数。
 * @param availablePermits 当前可用许可数。
 */
public record ConversationQueueSnapshot(
    String mode,
    int maxConcurrent,
    int activeCount,
    int waitingCount,
    int availablePermits
) {
}
