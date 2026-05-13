package com.codingx.backend.chat.domain.service;

public interface ChatStreamPublisher {

    void publishUserMessage(Long conversationId, String content);

    void publishAssistantDelta(Long conversationId, String delta);

    void publishAssistantCompleted(Long conversationId, String content);

    void publishError(Long conversationId, String message);
}