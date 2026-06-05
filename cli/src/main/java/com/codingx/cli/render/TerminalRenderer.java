package com.codingx.cli.render;

import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.AgentEventType;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 Agent 事件转换为终端可读文本；后续 tui4j UI 可复用同一渲染语义。
 */
public class TerminalRenderer {

    /**
     * 批量渲染事件。
     *
     * @param events Agent 事件列表。
     * @return 终端输出行。
     */
    public List<String> render(List<AgentEvent> events) {
        List<String> lines = new ArrayList<>();
        for (AgentEvent event : events) {
            lines.add(render(event));
        }
        return lines;
    }

    /**
     * 渲染单个事件。
     *
     * @param event Agent 事件。
     * @return 终端输出文本。
     */
    public String render(AgentEvent event) {
        AgentEventType type = event.eventType();
        return switch (type) {
            case SESSION_STARTED -> "[session] " + event.sessionId();
            case TURN_STARTED -> "[turn] 开始任务: " + event.payloadText("task");
            case ASSISTANT_DELTA -> event.payloadText("delta");
            case THINKING_DELTA -> "[thinking] " + event.payloadText("delta");
            case TOOL_STARTED -> "[tool] 工具: " + event.payloadText("toolId");
            case TOOL_OUTPUT_DELTA -> "[tool:output] " + event.payloadText("delta");
            case TOOL_COMPLETED -> "[tool] 完成: " + event.payloadText("toolId");
            case COMMAND_STARTED -> "[cmd] " + event.payloadText("command");
            case COMMAND_OUTPUT_DELTA -> "[cmd:" + event.payloadText("stream") + "] " + event.payloadText("delta");
            case COMMAND_COMPLETED -> "[cmd] 退出码: " + event.payloadText("exitCode");
            case FILE_DIFF -> "[diff] " + event.payloadText("path");
            case APPROVAL_REQUESTED -> "[approval] 需要审批 [" + event.payloadText("risk") + "] "
                + event.payloadText("reason") + " - " + event.payloadText("summary");
            case APPROVAL_RESOLVED -> "[approval] 已处理: " + event.payloadText("result");
            case TURN_COMPLETED -> "[turn] 任务完成: " + event.payloadText("status");
            case TURN_INTERRUPTED -> "[turn] 已中断";
            case ERROR -> "[error] " + event.payloadText("message");
            case UNKNOWN -> "[event] 未知事件";
        };
    }
}
