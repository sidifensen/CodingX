package com.codingx.chat.infrastructure.stream;

import com.codingx.chat.domain.port.ChatStreamPublisher;
import org.springframework.stereotype.Component;

/**
 * 空实现的聊天流式事件发布器，供不需要实时推送的测试或降级场景使用。
 */
@Component
public class NoopChatStreamPublisher implements ChatStreamPublisher {

    /**
     * 忽略用户消息发布。
     * @param conversationId 会话标识。
     * @param content 用户消息正文。
     */
    @Override
    public void publishUserMessage(Long conversationId, String content) {
    }

    /**
     * 忽略助手正文增量发布。
     * @param conversationId 会话标识。
     * @param delta 助手正文增量。
     */
    @Override
    public void publishAssistantDelta(Long conversationId, String delta) {
    }

    @Override
    public void publishAssistantThinkingDelta(Long conversationId, String delta) {
    }

    @Override
    public void publishStep(Long conversationId, Object payload) {
    }

    @Override
    public void publishMcpCall(Long conversationId, Object payload) {
    }

    @Override
    public void publishToolCall(Long conversationId, Object payload) {
    }

    /**
     * 忽略危险命令确认请求发布。
     * @param conversationId 会话标识。
     * @param payload 审批请求载荷。
     */
    @Override
    public void publishApprovalRequest(Long conversationId, Object payload) {
    }

    @Override
    public void publishReference(Long conversationId, Object payload) {
    }

    @Override
    public void publishArtifact(Long conversationId, Object payload) {
    }

    /**
     * 忽略目标状态事件发布。
     * @param conversationId 会话标识。
     * @param payload 目标快照载荷。
     */
    @Override
    public void publishGoal(Long conversationId, Object payload) {
    }

    /**
     * 忽略 Hook 桌面通知事件。
     * @param conversationId 会话标识。
     * @param payload Hook 通知载荷。
     */
    @Override
    public void publishHookNotification(Long conversationId, Object payload) {
    }

    /**
     * 忽略助手完成事件。
     * @param conversationId 会话标识。
     * @param content 助手完整回复。
     * @param title 会话标题。
     */
    @Override
    public void publishAssistantCompleted(Long conversationId, String content, String title) {
    }

    /**
     * 发布主动取消事件。
     * @param conversationId 会话标识。
     */
    @Override
    public void publishCancelled(Long conversationId) {
    }

    /**
     * 发布排队拒绝事件。
     * @param conversationId 会话标识。
     * @param reason 拒绝原因。
     */
    @Override
    public void publishRejected(Long conversationId, String reason) {
    }

    @Override
    public void publishQueued(Long conversationId, int position) {
    }

    @Override
    public void publishQueueAccepted(Long conversationId) {
    }

    /**
     * 忽略错误事件发布。
     * @param conversationId 会话标识。
     * @param message 错误文案。
     */
    @Override
    public void publishError(Long conversationId, String message) {
    }
}

