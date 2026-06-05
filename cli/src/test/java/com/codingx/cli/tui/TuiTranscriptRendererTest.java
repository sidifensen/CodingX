package com.codingx.cli.tui;

import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.AgentEventType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 截图风格 transcript 渲染测试，直接验证 AgentEvent 到 TUI 文本的映射。
 */
class TuiTranscriptRendererTest {

    @Test
    void assistantEventShouldRenderAsConversationLine() {
        TuiTranscriptRenderer renderer = new TuiTranscriptRenderer();

        List<String> lines = renderer.render(List.of(event(AgentEventType.ASSISTANT_DELTA, Map.of(
            "delta", "我会先查看当前仓库结构"
        ))));

        assertTrue(String.join("\n", lines).contains("我会先查看当前仓库结构"));
    }

    @Test
    void toolEventsShouldRenderAsCompactStatusLines() {
        TuiTranscriptRenderer renderer = new TuiTranscriptRenderer();

        List<String> lines = renderer.render(List.of(
            event(AgentEventType.TOOL_STARTED, Map.of(
                "toolId", "grep",
                "displayName", "代码搜索"
            )),
            event(AgentEventType.TOOL_COMPLETED, Map.of(
                "toolId", "grep",
                "displayName", "代码搜索",
                "rawResult", "命中 2 处"
            ))
        ));

        String output = String.join("\n", lines);
        assertTrue(output.contains("代码搜索"));
        assertTrue(output.contains("命中 2 处"));
    }

    @Test
    void turnCompletedShouldRenderTaskCompletionLine() {
        TuiTranscriptRenderer renderer = new TuiTranscriptRenderer();

        List<String> lines = renderer.render(List.of(event(AgentEventType.TURN_COMPLETED, Map.of(
            "status", "COMPLETED"
        ))));

        assertTrue(String.join("\n", lines).contains("Task completed: COMPLETED"));
    }

    @Test
    void errorShouldRenderErrorLine() {
        TuiTranscriptRenderer renderer = new TuiTranscriptRenderer();

        List<String> lines = renderer.render(List.of(event(AgentEventType.ERROR, Map.of(
            "message", "模拟错误"
        ))));

        assertTrue(String.join("\n", lines).contains("! Error"));
    }

    private AgentEvent event(AgentEventType eventType, Map<String, Object> payload) {
        // 测试只关心事件类型和载荷映射，固定 session/turn 可以避免样例噪音。
        return AgentEvent.of("session", "turn", 1, eventType, payload);
    }
}
