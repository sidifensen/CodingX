package com.codingx.chat.infrastructure.stream;
import com.codingx.chat.domain.service.ChatStreamPublisher;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 负责 SseChatStreamPublisher 的流式事件发布。
 */
@Component
@Primary
@RequiredArgsConstructor
public class SseChatStreamPublisher implements ChatStreamPublisher {

    /**
     * ChatSseRegistry 依赖。
     */
    private final ChatSseRegistry chatSseRegistry;

    /**
     * 发布 publishUserMessage 处理的更新内容。
     * @param conversationId 输入参数。
     * @param content 输入参数。
     */
    @Override
    public void publishUserMessage(Long conversationId, String content) {
        chatSseRegistry.publish(conversationId, "chat-user-message", Map.of("content", content));
    }

    /**
     * 发布 publishAssistantDelta 处理的更新内容。
     * @param conversationId 输入参数。
     * @param delta 输入参数。
     */
    @Override
    public void publishAssistantDelta(Long conversationId, String delta) {
        chatSseRegistry.publish(conversationId, "chat-assistant-delta", Map.of("delta", delta));
    }

    /**
     * 发布 publishAssistantCompleted 处理的更新内容。
     * @param conversationId 输入参数。
     * @param content 输入参数。
     */
    @Override
    public void publishAssistantCompleted(Long conversationId, String content) {
        chatSseRegistry.publish(conversationId, "chat-assistant-completed", Map.of("content", content));
    }

    /**
     * 发布 publishError 处理的更新内容。
     * @param conversationId 输入参数。
     * @param message 输入参数。
     */
    @Override
    public void publishError(Long conversationId, String message) {
        chatSseRegistry.publish(conversationId, "chat-error", Map.of("message", message));
    }
}
