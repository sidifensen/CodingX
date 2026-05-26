package com.codingx.chat.infrastructure.stream;
import com.codingx.chat.domain.port.ChatStreamPublisher;
import org.springframework.stereotype.Component;

/**
 * 负责 NoopChatStreamPublisher 的流式事件发布。
 */
@Component
public class NoopChatStreamPublisher implements ChatStreamPublisher {

    /**
     * 发布 publishUserMessage 处理的更新内容。
     * @param conversationId 输入参数。
     * @param content 输入参数。
     */
    @Override
    public void publishUserMessage(Long conversationId, String content) {
    }

    /**
     * 发布 publishAssistantDelta 处理的更新内容。
     * @param conversationId 输入参数。
     * @param delta 输入参数。
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

    @Override
    public void publishReference(Long conversationId, Object payload) {
    }

    @Override
    public void publishArtifact(Long conversationId, Object payload) {
    }

    /**
     * 发布 publishAssistantCompleted 处理的更新内容。
     * @param conversationId 输入参数。
     * @param content 输入参数。
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
     * 发布 publishError 处理的更新内容。
     * @param conversationId 输入参数。
     * @param message 输入参数。
     */
    @Override
    public void publishError(Long conversationId, String message) {
    }
}

