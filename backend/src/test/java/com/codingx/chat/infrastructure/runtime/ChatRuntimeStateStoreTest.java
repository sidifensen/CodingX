package com.codingx.chat.infrastructure.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
        ChatRuntimeStateStore store = new TestChatRuntimeStateStore();

        store.markActive(1001L);
        assertTrue(store.isActive(1001L));
        assertFalse(store.isCancelled(1001L));

        store.markCancelled(1001L);
        assertTrue(store.isCancelled(1001L));

        store.clear(1001L);
        assertFalse(store.isActive(1001L));
        assertFalse(store.isCancelled(1001L));
    }

    /**
     * 仅用于契约验证的本地测试实现，避免测试绑定生产环境的 Redis 依赖。
     */
    private static final class TestChatRuntimeStateStore implements ChatRuntimeStateStore {

        private final Map<Long, Boolean> activeConversations = new ConcurrentHashMap<>();
        private final Map<Long, Boolean> cancelledConversations = new ConcurrentHashMap<>();

        @Override
        public void markActive(Long conversationId) {
            activeConversations.put(conversationId, Boolean.TRUE);
            cancelledConversations.remove(conversationId);
        }

        @Override
        public void markCancelled(Long conversationId) {
            cancelledConversations.put(conversationId, Boolean.TRUE);
            activeConversations.remove(conversationId);
        }

        @Override
        public boolean isActive(Long conversationId) {
            return activeConversations.containsKey(conversationId);
        }

        @Override
        public boolean isCancelled(Long conversationId) {
            return cancelledConversations.containsKey(conversationId);
        }

        @Override
        public void clear(Long conversationId) {
            activeConversations.remove(conversationId);
            cancelledConversations.remove(conversationId);
        }
    }
}
