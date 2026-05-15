package com.codingx.chat.infrastructure.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 提供当前进程内的聊天运行态存储实现，后续可平滑替换为 Redis。
 */
@Component
@ConditionalOnProperty(prefix = "app.runtime", name = "use-redis-state-store", havingValue = "false", matchIfMissing = true)
public class InMemoryChatRuntimeStateStore implements ChatRuntimeStateStore {

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
