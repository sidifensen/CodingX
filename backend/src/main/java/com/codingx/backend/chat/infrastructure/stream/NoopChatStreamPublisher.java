package com.codingx.backend.chat.infrastructure.stream;
import com.codingx.backend.chat.domain.service.ChatStreamPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes streaming updates for NoopChatStreamPublisher.
 */
@Component
public class NoopChatStreamPublisher implements ChatStreamPublisher {

    /**
     * Publishes the update handled by publishUserMessage.
     * @param conversationId input argument.
     * @param content input argument.
     */
    @Override
    public void publishUserMessage(Long conversationId, String content) {
    }

    /**
     * Publishes the update handled by publishAssistantDelta.
     * @param conversationId input argument.
     * @param delta input argument.
     */
    @Override
    public void publishAssistantDelta(Long conversationId, String delta) {
    }

    /**
     * Publishes the update handled by publishAssistantCompleted.
     * @param conversationId input argument.
     * @param content input argument.
     */
    @Override
    public void publishAssistantCompleted(Long conversationId, String content) {
    }

    /**
     * Publishes the update handled by publishError.
     * @param conversationId input argument.
     * @param message input argument.
     */
    @Override
    public void publishError(Long conversationId, String message) {
    }
}
