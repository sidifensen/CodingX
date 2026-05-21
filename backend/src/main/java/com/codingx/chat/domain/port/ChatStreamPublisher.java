package com.codingx.chat.domain.port;

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
     * 发布思考增量事件，供前端展示模型思考过程。
     * @param conversationId 会话标识。
     * @param delta 思考增量。
     */
    void publishAssistantThinkingDelta(Long conversationId, String delta);

    /**
     * 发布执行步骤事件，供前端右栏在流式过程中增量更新。
     * @param conversationId 会话标识。
     * @param payload 步骤载荷。
     */
    void publishStep(Long conversationId, Object payload);

    /**
     * 发布 MCP 调用事件，供前端在消息区展示调用详情折叠面板。
     * @param conversationId 会话标识。
     * @param payload MCP 调用载荷。
     */
    void publishMcpCall(Long conversationId, Object payload);

    /**
     * 发布参考来源事件，供前端右栏在搜索完成后即时展示。
     * @param conversationId 会话标识。
     * @param payload 来源载荷。
     */
    void publishReference(Long conversationId, Object payload);

    /**
     * 发布产物事件，供前端右栏即时展示生成结果。
     * @param conversationId 会话标识。
     * @param payload 产物载荷。
     */
    void publishArtifact(Long conversationId, Object payload);

    /**
     * 发布 publishAssistantCompleted 处理的更新内容。
     * @param conversationId 输入参数。
     * @param content 输入参数。
     */
    void publishAssistantCompleted(Long conversationId, String content, String title);

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
     * 发布排队中事件，供前端展示队列位置与等待提示。
     * @param conversationId 会话标识。
     * @param position 当前排队位置（从1开始）。
     */
    void publishQueued(Long conversationId, int position);

    /**
     * 发布已获取执行资格事件，通知前端关闭排队提示。
     * @param conversationId 会话标识。
     */
    void publishQueueAccepted(Long conversationId);

    /**
     * 发布 publishError 处理的更新内容。
     * @param conversationId 输入参数。
     * @param message 输入参数。
     */
    void publishError(Long conversationId, String message);
}

