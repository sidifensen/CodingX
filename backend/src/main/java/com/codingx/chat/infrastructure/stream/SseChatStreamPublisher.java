package com.codingx.chat.infrastructure.stream;
import com.codingx.chat.domain.port.ChatStreamPublisher;
import java.util.LinkedHashMap;
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

    @Override
    public void publishAssistantThinkingDelta(Long conversationId, String delta) {
        chatSseRegistry.publish(conversationId, "thinking", Map.of("type", "thinking", "delta", delta));
    }

    @Override
    public void publishStep(Long conversationId, Object payload) {
        chatSseRegistry.publish(conversationId, "step", payload);
    }

    @Override
    public void publishMcpCall(Long conversationId, Object payload) {
        chatSseRegistry.publish(conversationId, "mcp-call", payload);
    }

    @Override
    public void publishToolCall(Long conversationId, Object payload) {
        chatSseRegistry.publish(conversationId, "tool-call", payload);
    }

    @Override
    public void publishReference(Long conversationId, Object payload) {
        chatSseRegistry.publish(conversationId, "reference", payload);
    }

    @Override
    public void publishArtifact(Long conversationId, Object payload) {
        chatSseRegistry.publish(conversationId, "artifact", payload);
    }

    /**
     * 发布助手回复完成事件。
     * @param conversationId 会话标识。
     * @param content 助手完整回复。
     * @param title 会话标题。
     */
    @Override
    public void publishAssistantCompleted(Long conversationId, String content, String title) {
        publishAssistantCompleted(conversationId, null, content, title);
    }

    /**
     * 发布已落库助手回复完成事件，完成载荷携带真实消息 ID，避免前端等待历史回放后才能启用消息操作。
     * @param conversationId 会话标识。
     * @param assistantMessageId 已落库助手消息主键。
     * @param content 助手完整回复。
     * @param title 会话标题。
     */
    @Override
    public void publishAssistantCompleted(Long conversationId, Long assistantMessageId, String content, String title) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", conversationId);
        if (assistantMessageId != null) {
            payload.put("assistantMessageId", assistantMessageId);
        }
        payload.put("content", content);
        payload.put("title", title);
        chatSseRegistry.publish(conversationId, "finish", payload);
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

    @Override
    public void publishQueued(Long conversationId, int position) {
        chatSseRegistry.publish(conversationId, "queued", Map.of(
            "conversationId", conversationId,
            "position", Math.max(1, position)
        ));
    }

    @Override
    public void publishQueueAccepted(Long conversationId) {
        chatSseRegistry.publish(conversationId, "queue-accepted", Map.of("conversationId", conversationId));
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

