package com.codingx.backend.chat.domain.service;

/**
 * Defines the domain service contract exposed by ChatStreamPublisher.
 */
public interface ChatStreamPublisher {

    /**
     * Publishes the update handled by publishUserMessage.
     * @param conversationId input argument.
     * @param content input argument.
     */
    void publishUserMessage(Long conversationId, String content);

    /**
     * Publishes the update handled by publishAssistantDelta.
     * @param conversationId input argument.
     * @param delta input argument.
     */
    void publishAssistantDelta(Long conversationId, String delta);

    /**
     * Publishes the update handled by publishAssistantCompleted.
     * @param conversationId input argument.
     * @param content input argument.
     */
    void publishAssistantCompleted(Long conversationId, String content);

    /**
     * Publishes the update handled by publishError.
     * @param conversationId input argument.
     * @param message input argument.
     */
    void publishError(Long conversationId, String message);
}
