package com.codingx.chat.infrastructure.stream;

import com.codingx.chat.domain.port.ChatStreamPublisher;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 基于 SSE 的聊天流式事件发布器，负责把领域流事件转换成前端可消费的事件名与载荷。
 */
@Component
@Primary
@RequiredArgsConstructor
public class SseChatStreamPublisher implements ChatStreamPublisher {

    /**
     * SSE 连接注册表，用于按会话 ID 定位连接、推送事件并在终态后关闭通道。
     */
    private final ChatSseRegistry chatSseRegistry;

    /**
     * 发布用户消息事件。
     * @param conversationId 会话标识。
     * @param content 用户消息正文。
     */
    @Override
    public void publishUserMessage(Long conversationId, String content) {
        // 单次 SSE 主入口下用户消息已由前端本地掌握，这里不再回放旧事件。
    }

    /**
     * 发布助手正文增量事件。
     * @param conversationId 会话标识。
     * @param delta 助手正文增量。
     */
    @Override
    public void publishAssistantDelta(Long conversationId, String delta) {
        // 步骤 1：正文增量统一使用 message 事件，前端按 type=response 追加到当前助手消息。
        chatSseRegistry.publish(conversationId, "message", Map.of("type", "response", "delta", delta));
    }

    /**
     * 发布助手思考增量事件。
     * @param conversationId 会话标识。
     * @param delta 思考内容增量。
     */
    @Override
    public void publishAssistantThinkingDelta(Long conversationId, String delta) {
        // 步骤 1：thinking 单独使用事件类型，避免与最终正文混写到同一消息内容。
        chatSseRegistry.publish(conversationId, "thinking", Map.of("type", "thinking", "delta", delta));
    }

    /**
     * 发布执行步骤事件。
     * @param conversationId 会话标识。
     * @param payload 步骤载荷。
     */
    @Override
    public void publishStep(Long conversationId, Object payload) {
        // 步骤 1：步骤载荷由应用层组装，发布器只负责事件路由，不拆解业务字段。
        chatSseRegistry.publish(conversationId, "step", payload);
    }

    /**
     * 发布 MCP 调用事件。
     * @param conversationId 会话标识。
     * @param payload MCP 调用载荷。
     */
    @Override
    public void publishMcpCall(Long conversationId, Object payload) {
        // 步骤 1：MCP 调用使用独立事件名，前端可渲染为消息内调用详情。
        chatSseRegistry.publish(conversationId, "mcp-call", payload);
    }

    /**
     * 发布本地工具调用事件。
     * @param conversationId 会话标识。
     * @param payload 工具调用载荷。
     */
    @Override
    public void publishToolCall(Long conversationId, Object payload) {
        // 步骤 1：模型工具调用与 MCP 调用分开发布，避免前端混淆不同工具来源。
        chatSseRegistry.publish(conversationId, "tool-call", payload);
    }

    /**
     * 发布参考来源事件。
     * @param conversationId 会话标识。
     * @param payload 来源载荷。
     */
    @Override
    public void publishReference(Long conversationId, Object payload) {
        // 步骤 1：搜索或资料引用完成后即时推送，前端右栏无需等待最终回复结束。
        chatSseRegistry.publish(conversationId, "reference", payload);
    }

    /**
     * 发布生成产物事件。
     * @param conversationId 会话标识。
     * @param payload 产物载荷。
     */
    @Override
    public void publishArtifact(Long conversationId, Object payload) {
        // 步骤 1：产物事件使用独立通道，前端可在过程面板即时展示文件或结构化结果。
        chatSseRegistry.publish(conversationId, "artifact", payload);
    }

    /**
     * 发布真实目标状态事件。
     * @param conversationId 会话标识。
     * @param payload 目标快照载荷。
     */
    @Override
    public void publishGoal(Long conversationId, Object payload) {
        // 步骤 1：目标状态由 ChatGoalService 组装，SSE 层只负责使用 goal 事件名推送。
        chatSseRegistry.publish(conversationId, "goal", payload);
    }

    /**
     * 发布助手回复完成事件。
     * @param conversationId 会话标识。
     * @param content 助手完整回复。
     * @param title 会话标题。
     */
    @Override
    public void publishAssistantCompleted(Long conversationId, String content, String title) {
        // 步骤 1：兼容未落库消息 ID 的本地临时会话，统一委托到完整终态发布方法。
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
        // 步骤 1：按前端契约组装 finish 载荷，assistantMessageId 存在时才写入避免伪造消息主键。
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", conversationId);
        if (assistantMessageId != null) {
            payload.put("assistantMessageId", assistantMessageId);
        }
        payload.put("content", content);
        payload.put("title", title);
        // 步骤 2：先发布 finish，再发布 done，最后关闭连接，保证前端收到完整终态数据后再收口。
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
        // 步骤 1：取消属于明确终态，需要通知前端并关闭 SSE 连接。
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
        // 步骤 1：排队拒绝直接进入终态，reason 作为前端展示的拒绝原因。
        chatSseRegistry.publish(conversationId, "reject", Map.of("conversationId", conversationId, "reason", reason));
        chatSseRegistry.publish(conversationId, "done", Map.of("conversationId", conversationId));
        chatSseRegistry.complete(conversationId);
    }

    /**
     * 发布排队中事件。
     * @param conversationId 会话标识。
     * @param position 当前排队位置。
     */
    @Override
    public void publishQueued(Long conversationId, int position) {
        // 步骤 1：队列位置最小为 1，避免并发计算产生 0 或负数时前端展示异常。
        chatSseRegistry.publish(conversationId, "queued", Map.of(
            "conversationId", conversationId,
            "position", Math.max(1, position)
        ));
    }

    /**
     * 发布已获得执行资格事件。
     * @param conversationId 会话标识。
     */
    @Override
    public void publishQueueAccepted(Long conversationId) {
        // 步骤 1：通知前端关闭排队提示，后续会继续接收模型流式事件。
        chatSseRegistry.publish(conversationId, "queue-accepted", Map.of("conversationId", conversationId));
    }

    /**
     * 发布错误事件并结束当前流式通道。
     * @param conversationId 会话标识。
     * @param message 返回给前端展示的中文错误文案。
     */
    @Override
    public void publishError(Long conversationId, String message) {
        // 步骤 1：错误终态先推送错误文案，再发送 done 并关闭连接，避免前端一直保持加载态。
        chatSseRegistry.publish(conversationId, "error", Map.of("message", message));
        chatSseRegistry.publish(conversationId, "done", Map.of("conversationId", conversationId));
        chatSseRegistry.complete(conversationId);
    }
}

