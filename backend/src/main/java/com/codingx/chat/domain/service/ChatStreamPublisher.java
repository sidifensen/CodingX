package com.codingx.chat.domain.service;

/**
 * 定义 ChatStreamPublisher 的领域服务契约。
 */
public interface ChatStreamPublisher {

    /**
     * 发布 publishUserMessage 处理的更新内容。
     * @param conversationId 输入参数。
     * @param content 输入参数。
     */
    void publishUserMessage(Long conversationId, String content);

    /**
     * 发布 publishAssistantDelta 处理的更新内容。
     * @param conversationId 输入参数。
     * @param delta 输入参数。
     */
    void publishAssistantDelta(Long conversationId, String delta);

    /**
     * 发布 publishAssistantCompleted 处理的更新内容。
     * @param conversationId 输入参数。
     * @param content 输入参数。
     */
    void publishAssistantCompleted(Long conversationId, String content);

    /**
     * 发布主动取消事件。
     * @param conversationId 会话标识。
     */
    void publishCancelled(Long conversationId);

    /**
     * 发布排队拒绝事件。
     * @param conversationId 会话标识。
     * @param reason 拒绝原因。
     */
    void publishRejected(Long conversationId, String reason);

    /**
     * 发布 publishError 处理的更新内容。
     * @param conversationId 输入参数。
     * @param message 输入参数。
     */
    void publishError(Long conversationId, String message);
}
