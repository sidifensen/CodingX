package com.codingx.chat.infrastructure.runtime;

/**
 * 抽象聊天运行态存储，屏蔽进程内与 Redis 两种实现的差异。
 */
public interface ChatRuntimeStateStore {

    /**
     * 标记指定会话处于活动状态。
     * @param conversationId 会话标识。
     */
    void markActive(Long conversationId);

    /**
     * 标记指定会话已取消。
     * @param conversationId 会话标识。
     */
    void markCancelled(Long conversationId);

    /**
     * 判断指定会话当前是否活动中。
     * @param conversationId 会话标识。
     * @return 是否活动中。
     */
    boolean isActive(Long conversationId);

    /**
     * 判断指定会话是否已被取消。
     * @param conversationId 会话标识。
     * @return 是否已取消。
     */
    boolean isCancelled(Long conversationId);

    /**
     * 清理指定会话的运行状态。
     * @param conversationId 会话标识。
     */
    void clear(Long conversationId);
}
