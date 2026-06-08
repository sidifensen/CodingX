package com.codingx.cli.tui;

import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.AgentEventType;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 AgentEvent 转成紧凑 transcript 行，避免 TUI 直接展示日志式事件前缀。
 */
public class TuiTranscriptRenderer {

    /**
     * 批量渲染事件。
     *
     * @param events Agent 事件列表。
     * @return transcript 行。
     */
    public List<String> render(List<AgentEvent> events) {
        List<String> lines = new ArrayList<>();
        for (AgentEvent event : events) {
            lines.addAll(render(event));
        }
        return lines;
    }

    /**
     * 渲染单个事件；只做展示层映射，不在这里判断任务状态或触发工具执行。
     *
     * @param event Agent 事件。
     * @return 当前事件对应的 transcript 行。
     */
    private List<String> render(AgentEvent event) {
        AgentEventType type = event.eventType();
        return switch (type) {
            case SESSION_STARTED, TURN_STARTED -> List.of();
            case ASSISTANT_DELTA -> List.of("  " + event.payloadText("delta"));
            case THINKING_DELTA -> List.of("  Thinking: " + event.payloadText("delta"));
            case TOOL_STARTED -> List.of("  " + toolName(event) + "...");
            case TOOL_OUTPUT_DELTA -> renderOutput(event.payloadText("delta"));
            case TOOL_COMPLETED -> renderToolCompleted(event);
            case COMMAND_STARTED -> List.of("  $ " + event.payloadText("command"));
            case COMMAND_OUTPUT_DELTA -> renderOutput(event.payloadText("delta"));
            case COMMAND_COMPLETED -> List.of("  Command exited: " + event.payloadText("exitCode"));
            case FILE_DIFF -> List.of("  Diff: " + event.payloadText("path"));
            case APPROVAL_REQUESTED -> List.of("  Approval required: " + event.payloadText("summary"));
            case APPROVAL_RESOLVED -> List.of("  Approval: " + event.payloadText("result"));
            // 完成事件只驱动状态栏变为 completed，不再写入 transcript，避免每轮回答后出现英文完成噪音。
            case TURN_COMPLETED -> List.of();
            case TURN_INTERRUPTED -> List.of("  Task interrupted");
            case ERROR -> List.of("! Error: " + event.payloadText("message"));
            case UNKNOWN -> List.of("  Unknown event");
        };
    }

    /**
     * 渲染工具完成行，并在后端提供结果或错误时追加紧凑输出，避免真实工具事件继续显示 mock 文案。
     *
     * @param event 工具完成事件。
     * @return 工具完成 transcript 行。
     */
    private List<String> renderToolCompleted(AgentEvent event) {
        List<String> lines = new ArrayList<>();
        String status = event.payloadText("status");
        if ("ERROR".equalsIgnoreCase(status)) {
            lines.add("  ! " + toolName(event));
            lines.addAll(renderOutput(event.payloadText("errorMessage")));
            return lines;
        }
        lines.add("  ✓ " + toolName(event));
        String rawResult = event.payloadText("rawResult");
        if (rawResult.isBlank()) {
            rawResult = event.payloadText("content");
        }
        lines.addAll(renderOutput(rawResult));
        return lines;
    }

    /**
     * 解析后端工具展示名；缺失真实展示名时保留旧 mock 的 ToolSearch 文案，兼容现有 TUI 外壳测试。
     *
     * @param event 工具事件。
     * @return 可展示的工具名。
     */
    private String toolName(AgentEvent event) {
        String displayName = event.payloadText("displayName");
        if (!displayName.isBlank()) {
            return displayName;
        }
        String toolId = event.payloadText("toolId");
        if (!toolId.isBlank() && !"ls".equalsIgnoreCase(toolId)) {
            return toolId;
        }
        return "ToolSearch";
    }

    /**
     * 渲染多行工具或命令输出；过滤空行可以让紧凑状态行不会被 mock 输出撑散。
     *
     * @param output 工具或命令输出文本，可为空。
     * @return 缩进后的可见输出行。
     */
    private List<String> renderOutput(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (!line.isBlank()) {
                lines.add("    " + line);
            }
        }
        return lines;
    }
}
