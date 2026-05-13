package com.codingx.backend.chat.domain.service;

import com.codingx.backend.chat.domain.model.ChatMessage;
import java.util.List;

public interface AiChatClient {

    void streamChat(List<ChatMessage> history, StreamHandler handler);

    interface StreamHandler {
        void onDelta(String delta);

        void onComplete();

        default void onError(Throwable throwable) {
        }
    }
}