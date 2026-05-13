package com.codingx.backend.chat.infrastructure.stream;
import com.codingx.backend.chat.domain.service.ChatStreamPublisher;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Publishes streaming updates for SseChatStreamPublisher.
 */
@Component
@Primary
@RequiredArgsConstructor
public class SseChatStreamPublisher implements ChatStreamPublisher {

    /**
     * chatSseRegistry value.
     */
    private final ChatSseRegistry chatSseRegistry;

    /**
     * Publishes the update handled by publishUserMessage.
     * @param conversationId input argument.
     * @param content input argument.
     */
    @Override
    public void publishUserMessage(Long conversationId, String content) {
        chatSseRegistry.publish(conversationId, "chat-user-message", Map.of("content", content));
    }

    /**
     * Publishes the update handled by publishAssistantDelta.
     * @param conversationId input argument.
     * @param delta input argument.
     */
    @Override
    public void publishAssistantDelta(Long conversationId, String delta) {
        chatSseRegistry.publish(conversationId, "chat-assistant-delta", Map.of("delta", delta));
    }

    /**
     * Publishes the update handled by publishAssistantCompleted.
     * @param conversationId input argument.
     * @param content input argument.
     */
    @Override
    public void publishAssistantCompleted(Long conversationId, String content) {
        chatSseRegistry.publish(conversationId, "chat-assistant-completed", Map.of("content", content));
    }

    /**
     * Publishes the update handled by publishError.
     * @param conversationId input argument.
     * @param message input argument.
     */
    @Override
    public void publishError(Long conversationId, String message) {
        chatSseRegistry.publish(conversationId, "chat-error", Map.of("message", message));
    }
}
