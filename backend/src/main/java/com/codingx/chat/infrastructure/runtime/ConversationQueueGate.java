package com.codingx.chat.infrastructure.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 提供聊天链路最小并发门控，后续可替换为 Redis 分布式实现。
 */
@Component
public class ConversationQueueGate {

    private final int maxConcurrent;
    private final Map<Long, Boolean> activeConversations = new ConcurrentHashMap<>();

    /**
     * 使用默认并发上限创建门控实例。
     */
    public ConversationQueueGate() {
        this(2);
    }

    /**
     * 使用给定并发上限创建门控实例。
     * @param maxConcurrent 最大并发数。
     */
    public ConversationQueueGate(int maxConcurrent) {
        this.maxConcurrent = Math.max(1, maxConcurrent);
    }

    /**
     * 尝试为指定会话获取执行资格。
     * @param conversationId 会话标识。
     * @return 获取结果。
     */
    public synchronized QueueAcquireResult tryAcquire(Long conversationId) {
        if (activeConversations.containsKey(conversationId)) {
            return QueueAcquireResult.granted();
        }
        if (activeConversations.size() >= maxConcurrent) {
            return QueueAcquireResult.rejected("busy");
        }
        activeConversations.put(conversationId, Boolean.TRUE);
        return QueueAcquireResult.granted();
    }

    /**
     * 释放指定会话占用的执行资格。
     * @param conversationId 会话标识。
     */
    public synchronized void release(Long conversationId) {
        activeConversations.remove(conversationId);
    }
}
