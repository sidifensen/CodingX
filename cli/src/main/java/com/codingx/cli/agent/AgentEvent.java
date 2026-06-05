package com.codingx.cli.agent;

import java.time.Instant;
import java.util.Map;

/**
 * Agent 事件统一信封，CLI 和 Web 后续都按该结构消费运行时事件。
 *
 * @param sessionId 会话标识。
 * @param turnId 任务轮次标识。
 * @param sequence 会话内递增序号。
 * @param eventType 事件类型。
 * @param payload 事件载荷。
 * @param createdAt 创建时间。
 */
public record AgentEvent(
    String sessionId,
    String turnId,
    long sequence,
    AgentEventType eventType,
    Map<String, Object> payload,
    Instant createdAt
) {
    /**
     * 构造测试和 mock 事件源使用的即时事件。
     *
     * @param sessionId 会话标识。
     * @param turnId 任务轮次标识。
     * @param sequence 会话内递增序号。
     * @param eventType 事件类型。
     * @param payload 事件载荷。
     * @return Agent 事件。
     */
    public static AgentEvent of(
        String sessionId,
        String turnId,
        long sequence,
        AgentEventType eventType,
        Map<String, Object> payload
    ) {
        return new AgentEvent(sessionId, turnId, sequence, eventType, payload, Instant.now());
    }

    /**
     * 读取 payload 文本字段；缺失字段统一返回空串，避免渲染层重复做 null 判断。
     *
     * @param key 字段名。
     * @return 文本值。
     */
    public String payloadText(String key) {
        Object value = payload.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
