package com.codingx.backend.chat.infrastructure.stream;

import com.codingx.backend.chat.domain.service.ChatStreamPublisher;
import org.springframework.stereotype.Component;

@Component
public class NoopChatStreamPublisher implements ChatStreamPublisher {

    @Override
    public void publishUserMessage(Long conversationId, String content) {
    }

    @Override
    public void publishAssistantDelta(Long conversationId, String delta) {
    }

    @Override
    public void publishAssistantCompleted(Long conversationId, String content) {
    }

    @Override
    public void publishError(Long conversationId, String message) {
    }
}