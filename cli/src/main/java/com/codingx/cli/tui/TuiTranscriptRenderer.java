package com.codingx.cli.tui;

import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.AgentEventType;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 AgentEvent 转成截图风格 transcript 行，避免 TUI 直接展示日志式事件前缀。
 */
public class TuiTranscriptRenderer {

    /**
     * mock 阶段没有真实工具耗时，固定展示 0.0s；真实事件源接入 duration 后再替换。
     */
    private static final String MOCK_TOOL_DURATION = "0.0s";

    /**
     * mock 阶段没有真实综合耗时，固定展示 7s，用于先贴近截图中的阶段状态。
     */
    private static final String MOCK_SYNTHESIS_DURATION = "7s";

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
            case ASSISTANT_DELTA -> List.of("  • " + event.payloadText("delta"));
            case THINKING_DELTA -> List.of("  " + event.payloadText("delta"));
            case TOOL_STARTED -> List.of("  ToolSearch...");
            case TOOL_OUTPUT_DELTA -> renderOutput(event.payloadText("delta"));
            case TOOL_COMPLETED -> List.of("  ✓ ToolSearch (" + MOCK_TOOL_DURATION + ")");
            case COMMAND_STARTED -> List.of("  $ " + event.payloadText("command"));
            case COMMAND_OUTPUT_DELTA -> renderOutput(event.payloadText("delta"));
            case COMMAND_COMPLETED -> List.of("  Command exited: " + event.payloadText("exitCode"));
            case FILE_DIFF -> List.of("  Diff: " + event.payloadText("path"));
            case APPROVAL_REQUESTED -> List.of("  Approval required: " + event.payloadText("summary"));
            case APPROVAL_RESOLVED -> List.of("  Approval: " + event.payloadText("result"));
            case TURN_COMPLETED -> List.of(
                "  Synthesizing...  (" + MOCK_SYNTHESIS_DURATION + ")",
                "  Task completed: " + event.payloadText("status")
            );
            case TURN_INTERRUPTED -> List.of("  Task interrupted");
            case ERROR -> List.of("! Error: " + event.payloadText("message"));
            case UNKNOWN -> List.of("  Unknown event");
        };
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
