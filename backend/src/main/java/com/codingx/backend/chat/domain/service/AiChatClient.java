package com.codingx.backend.chat.domain.service;
import com.codingx.backend.chat.domain.model.ChatMessage;
import java.util.List;

/**
 * Defines the domain service contract exposed by AiChatClient.
 */
public interface AiChatClient {

    /**
     * Streams the result handled by streamChat.
     * @param history input argument.
     * @param handler input argument.
     */
    void streamChat(List<ChatMessage> history, StreamHandler handler);

    /**
     * Defines the domain service contract exposed by StreamHandler.
     */
    interface StreamHandler {
        /**
         * Executes the logic defined by onDelta.
         * @param delta input argument.
         */
        void onDelta(String delta);

        /**
         * Executes the logic defined by onComplete.
         */
        void onComplete();

        /**
         * Executes the logic defined by onError.
         * @param throwable input argument.
         */
        default void onError(Throwable throwable) {
        }
    }
}
