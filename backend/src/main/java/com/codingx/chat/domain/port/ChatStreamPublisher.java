package com.codingx.chat.domain.port;

/**
 * 聊天流式事件发布端口，应用层只描述要发布的领域事件，具体 SSE 或空实现由基础设施层承接。
 */
public interface ChatStreamPublisher {

    /**
     * 发布用户消息事件。
     * @param conversationId 会话标识。
     * @param content 用户消息正文。
     */
    void publishUserMessage(Long conversationId, String content);

    /**
     * 发布助手正文增量事件。
     * @param conversationId 会话标识。
     * @param delta 助手回复正文增量。
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
     * 发布通用模型工具调用事件，供前端把本地工具调用展示为消息内过程链路。
     * @param conversationId 会话标识。
     * @param payload 工具调用载荷。
     */
    void publishToolCall(Long conversationId, Object payload);

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
     * 发布助手回复完成事件。
     * 业务约束：本地临时会话没有云端消息主键，继续使用该兼容入口，不向前端伪造可反馈的消息 ID。
     * @param conversationId 会话标识。
     * @param content 助手完整回复。
     * @param title 会话标题。
     */
    void publishAssistantCompleted(Long conversationId, String content, String title);

    /**
     * 发布已落库助手回复完成事件。
     * 业务约束：云端聊天必须带上助手消息主键，前端据此立即启用点赞、重新生成等消息级操作。
     * @param conversationId 会话标识。
     * @param assistantMessageId 已落库助手消息主键。
     * @param content 助手完整回复。
     * @param title 会话标题。
     */
    default void publishAssistantCompleted(Long conversationId, Long assistantMessageId, String content, String title) {
        publishAssistantCompleted(conversationId, content, title);
    }

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
     * 发布聊天执行错误事件，并结束当前流式通道。
     * @param conversationId 会话标识。
     * @param message 返回给前端展示的中文错误文案。
     */
    void publishError(Long conversationId, String message);
}

