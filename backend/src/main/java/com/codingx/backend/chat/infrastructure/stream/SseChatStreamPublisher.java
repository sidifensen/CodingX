package com.codingx.backend.chat.infrastructure.stream;

import com.codingx.backend.chat.domain.service.ChatStreamPublisher;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
public class SseChatStreamPublisher implements ChatStreamPublisher {

    private final ChatSseRegistry chatSseRegistry;

    @Override
    public void publishUserMessage(Long conversationId, String content) {
        chatSseRegistry.publish(conversationId, "chat-user-message", Map.of("content", content));
    }

    @Override
    public void publishAssistantDelta(Long conversationId, String delta) {
        chatSseRegistry.publish(conversationId, "chat-assistant-delta", Map.of("delta", delta));
    }

    @Override
    public void publishAssistantCompleted(Long conversationId, String content) {
        chatSseRegistry.publish(conversationId, "chat-assistant-completed", Map.of("content", content));
    }

    @Override
    public void publishError(Long conversationId, String message) {
        chatSseRegistry.publish(conversationId, "chat-error", Map.of("message", message));
    }
}