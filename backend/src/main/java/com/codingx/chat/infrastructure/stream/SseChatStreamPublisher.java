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
        // 单次 SSE 主入口下用户消息已由前端本地掌握，这里不再回放旧事件。
    }

    /**
     * 发布 publishAssistantDelta 处理的更新内容。
     * @param conversationId 输入参数。
     * @param delta 输入参数。
     */
    @Override
    public void publishAssistantDelta(Long conversationId, String delta) {
        chatSseRegistry.publish(conversationId, "message", Map.of("type", "response", "delta", delta));
    }

    /**
     * 发布 publishAssistantCompleted 处理的更新内容。
     * @param conversationId 输入参数。
     * @param content 输入参数。
     */
    @Override
    public void publishAssistantCompleted(Long conversationId, String content) {
        chatSseRegistry.publish(conversationId, "finish", Map.of("conversationId", conversationId, "content", content));
        chatSseRegistry.publish(conversationId, "done", Map.of("conversationId", conversationId));
        chatSseRegistry.complete(conversationId);
    }

    /**
     * 发布主动取消事件。
     * @param conversationId 会话标识。
     */
    @Override
    public void publishCancelled(Long conversationId) {
        chatSseRegistry.publish(conversationId, "cancel", Map.of("conversationId", conversationId));
        chatSseRegistry.publish(conversationId, "done", Map.of("conversationId", conversationId));
        chatSseRegistry.complete(conversationId);
    }

    /**
     * 发布排队拒绝事件。
     * @param conversationId 会话标识。
     * @param reason 拒绝原因。
     */
    @Override
    public void publishRejected(Long conversationId, String reason) {
        chatSseRegistry.publish(conversationId, "reject", Map.of("conversationId", conversationId, "reason", reason));
        chatSseRegistry.publish(conversationId, "done", Map.of("conversationId", conversationId));
        chatSseRegistry.complete(conversationId);
    }

    /**
     * 发布 publishError 处理的更新内容。
     * @param conversationId 输入参数。
     * @param message 输入参数。
     */
    @Override
    public void publishError(Long conversationId, String message) {
        chatSseRegistry.publish(conversationId, "error", Map.of("message", message));
        chatSseRegistry.publish(conversationId, "done", Map.of("conversationId", conversationId));
        chatSseRegistry.complete(conversationId);
    }
}
