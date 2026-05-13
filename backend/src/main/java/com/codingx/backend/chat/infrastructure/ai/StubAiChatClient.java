package com.codingx.backend.chat.infrastructure.ai;
import com.codingx.backend.chat.domain.model.ChatMessage;
import com.codingx.backend.chat.domain.service.AiChatClient;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Integrates the external AI provider used by StubAiChatClient.
 */
@Component
public class StubAiChatClient implements AiChatClient {

    /**
     * Streams the result handled by streamChat.
     * @param history input argument.
     * @param handler input argument.
     */
    @Override
    public void streamChat(List<ChatMessage> history, StreamHandler handler) {
        handler.onDelta("Stub response");
        handler.onComplete();
    }
}
