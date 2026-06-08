package com.codingx.cli.backend;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.AgentEventType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 将后端聊天 SSE 事件映射为 CLI 统一 `AgentEvent`，让 TUI 渲染层无需理解 HTTP 协议。
 */
class BackendChatEventMapper {

    /**
     * CLI 本轮本地生成的临时 turn 标识；后端 meta 到达后会优先使用 runId/taskId。
     */
    private final String fallbackTurnId;

    /**
     * 当前会话标识，meta 或 finish 到达后更新。
     */
    private String sessionId;

    /**
     * 当前运行标识，meta 中 runId/taskId 到达后更新。
     */
    private String turnId;

    /**
     * 本轮事件递增序号，保证 transcript 顺序稳定。
     */
    private final AtomicLong sequence = new AtomicLong(1);

    /**
     * @param initialSessionId 配置中的最近会话标识，可为空。
     * @param fallbackTurnId 本地兜底 turn 标识。
     */
    BackendChatEventMapper(String initialSessionId, String fallbackTurnId) {
        this.sessionId = StrUtil.blankToDefault(initialSessionId, "new");
        this.fallbackTurnId = fallbackTurnId;
        this.turnId = fallbackTurnId;
    }

    /**
     * 创建本轮任务开始事件，用于 TUI 知道真实后端请求已经发起。
     *
     * @param task 用户任务。
     * @param workspace 当前工作区。
     * @return 任务开始事件。
     */
    AgentEvent turnStarted(String task, String workspace) {
        return event(AgentEventType.TURN_STARTED, Map.of(
            "task", task,
            "workspace", workspace
        ));
    }

    /**
     * 创建错误事件，统一网络异常、HTTP 错误和解析错误的展示路径。
     *
     * @param message 展示给用户的中文错误。
     * @return 错误事件。
     */
    AgentEvent error(String message) {
        return event(AgentEventType.ERROR, Map.of(
            "message", StrUtil.blankToDefault(message, "聊天流请求失败")
        ));
    }

    /**
     * 将单个 SSE 事件映射为 0 到多个 CLI 事件。
     *
     * @param sseEvent 后端 SSE 事件。
     * @return CLI 事件列表。
     */
    List<AgentEvent> map(SseEvent sseEvent) {
        Map<String, Object> payload = parsePayload(sseEvent.data());
        updateRuntimeIds(payload);
        return switch (sseEvent.eventName()) {
            case "meta" -> List.of(event(AgentEventType.SESSION_STARTED, payload));
            case "message" -> mapMessage(payload);
            case "thinking" -> mapThinking(payload);
            case "tool-call", "mcp-call" -> mapToolCall(payload);
            case "step", "reference", "artifact" -> mapProcessEvent(sseEvent.eventName(), payload);
            case "finish" -> List.of(event(AgentEventType.TURN_COMPLETED, withDefault(payload, "status", "COMPLETED")));
            case "cancel" -> List.of(event(AgentEventType.TURN_INTERRUPTED, payload));
            case "reject" -> List.of(error(StrUtil.blankToDefault(text(payload, "reason"), "当前请求被拒绝")));
            case "queued" -> List.of(event(AgentEventType.TOOL_OUTPUT_DELTA, Map.of(
                "delta", "排队中，当前位置: " + StrUtil.blankToDefault(text(payload, "position"), "1")
            )));
            // 后端队列接收只是调度状态，TUI 已有 Working 行表达运行中，避免 transcript 出现“已开始执行”噪音。
            case "queue-accepted" -> List.of();
            case "error" -> List.of(error(text(payload, "message")));
            case "done" -> List.of();
            default -> List.of(event(AgentEventType.UNKNOWN, withDefault(payload, "eventName", sseEvent.eventName())));
        };
    }

    /**
     * 提取事件中的会话标识，供事件源写回 CLI 配置。
     *
     * @param sseEvent 后端 SSE 事件。
     * @return 会话标识；缺失时为空串。
     */
    String conversationId(SseEvent sseEvent) {
        Map<String, Object> payload = parsePayload(sseEvent.data());
        return text(payload, "conversationId");
    }

    /**
     * 解析助手正文增量；非 response 类型忽略，避免把控制事件写进回答文本。
     */
    private List<AgentEvent> mapMessage(Map<String, Object> payload) {
        if (!"response".equals(text(payload, "type"))) {
            return List.of();
        }
        return List.of(event(AgentEventType.ASSISTANT_DELTA, payload));
    }

    /**
     * 解析思考增量；后端约定 type=thinking 时才展示到思考区域。
     */
    private List<AgentEvent> mapThinking(Map<String, Object> payload) {
        if (!"thinking".equals(text(payload, "type"))) {
            return List.of();
        }
        return List.of(event(AgentEventType.THINKING_DELTA, payload));
    }

    /**
     * 根据后端工具阶段映射开始、进度、完成和异常。
     */
    private List<AgentEvent> mapToolCall(Map<String, Object> payload) {
        String phase = StrUtil.blankToDefault(text(payload, "phase"), "start").toLowerCase();
        if ("complete".equals(phase)) {
            return List.of(event(AgentEventType.TOOL_COMPLETED, payload));
        }
        if ("error".equals(phase)) {
            List<AgentEvent> events = new ArrayList<>();
            events.add(event(AgentEventType.TOOL_COMPLETED, withDefault(payload, "status", "ERROR")));
            events.add(error(StrUtil.blankToDefault(text(payload, "errorMessage"), "工具执行失败")));
            return events;
        }
        if ("progress".equals(phase)) {
            String delta = StrUtil.blankToDefault(text(payload, "content"), text(payload, "progressText"));
            return delta.isBlank() ? List.of() : List.of(event(AgentEventType.TOOL_OUTPUT_DELTA, Map.of("delta", delta)));
        }
        return List.of(event(AgentEventType.TOOL_STARTED, payload));
    }

    /**
     * 将步骤、引用和产物事件降级为过程输出，先保证 TUI 能看到后端过程信息。
     */
    private List<AgentEvent> mapProcessEvent(String eventName, Map<String, Object> payload) {
        Map<String, Object> nextPayload = new LinkedHashMap<>(payload);
        nextPayload.put("eventName", eventName);
        nextPayload.put("delta", StrUtil.blankToDefault(text(payload, "summary"), JSONUtil.toJsonStr(payload)));
        return List.of(event(AgentEventType.TOOL_OUTPUT_DELTA, nextPayload));
    }

    /**
     * 根据 meta/finish 中的后端字段刷新 session 与 turn，上游渲染保持同一个会话上下文。
     */
    private void updateRuntimeIds(Map<String, Object> payload) {
        String conversationId = text(payload, "conversationId");
        if (StrUtil.isNotBlank(conversationId)) {
            sessionId = conversationId;
        }
        String runId = StrUtil.blankToDefault(text(payload, "runId"), text(payload, "taskId"));
        if (StrUtil.isNotBlank(runId)) {
            turnId = runId;
        } else if (StrUtil.isBlank(turnId)) {
            turnId = fallbackTurnId;
        }
    }

    /**
     * 创建统一事件信封，并写入当前 session/turn 与递增序号。
     */
    private AgentEvent event(AgentEventType eventType, Map<String, Object> payload) {
        return new AgentEvent(
            StrUtil.blankToDefault(sessionId, "new"),
            StrUtil.blankToDefault(turnId, fallbackTurnId),
            sequence.getAndIncrement(),
            eventType,
            payload,
            Instant.now()
        );
    }

    /**
     * JSON data 解析失败时保留原始文本，避免协议异常时丢失排障信息。
     */
    private Map<String, Object> parsePayload(String data) {
        if (StrUtil.isBlank(data)) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(JSONUtil.parseObj(data));
        } catch (RuntimeException exception) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("rawData", data);
            payload.put("delta", data);
            return payload;
        }
    }

    /**
     * 读取 payload 文本字段，统一处理 null。
     */
    private String text(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 在不覆盖后端原字段的前提下补充展示默认值。
     */
    private Map<String, Object> withDefault(Map<String, Object> payload, String key, String value) {
        Map<String, Object> nextPayload = new LinkedHashMap<>(payload);
        nextPayload.putIfAbsent(key, value);
        return nextPayload;
    }
}
