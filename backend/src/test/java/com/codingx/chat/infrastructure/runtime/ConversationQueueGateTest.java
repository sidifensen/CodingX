package com.codingx.chat.infrastructure.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 验证聊天队列门控的最小并发与拒绝行为。
 */
class ConversationQueueGateTest {

    /**
     * 超过并发上限时应立即拒绝新的会话执行。
     */
    @Test
    void acquireRejectsWhenConcurrencyLimitReached() {
        ConversationQueueGate gate = new ConversationQueueGate(1);

        assertTrue(gate.tryAcquire(1001L).allowed());
        QueueAcquireResult rejected = gate.tryAcquire(1002L);

        assertFalse(rejected.allowed());
        assertEquals("busy", rejected.reason());
    }

    /**
     * 释放 permit 后后续请求应可继续获取执行资格。
     */
    @Test
    void releaseAllowsNextConversationToProceed() {
        ConversationQueueGate gate = new ConversationQueueGate(1);
        gate.tryAcquire(1001L);

        gate.release(1001L);
        QueueAcquireResult result = gate.tryAcquire(1002L);

        assertTrue(result.allowed());
    }
}
