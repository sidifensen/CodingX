package com.codingx.chat.infrastructure.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 验证聊天运行状态存储抽象的最小读写契约。
 */
class ChatRuntimeStateStoreTest {

    /**
     * 存储应支持活动态标记、取消标记与清理。
     */
    @Test
    void marksActiveCancelledAndClearsState() {
        ChatRuntimeStateStore store = new InMemoryChatRuntimeStateStore();

        store.markActive(1001L);
        assertTrue(store.isActive(1001L));
        assertFalse(store.isCancelled(1001L));

        store.markCancelled(1001L);
        assertTrue(store.isCancelled(1001L));

        store.clear(1001L);
        assertFalse(store.isActive(1001L));
        assertFalse(store.isCancelled(1001L));
    }
}
