package com.codingx.cli.render;

import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.AgentEventType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 终端渲染测试，锁定 AgentEvent 到用户可读文本的基础映射。
 */
class TerminalRendererTest {

    @Test
    void rendererShouldPrintAssistantToolCommandAndApprovalEvents() {
        TerminalRenderer renderer = new TerminalRenderer();
        List<String> lines = renderer.render(List.of(
            AgentEvent.of("s1", "t1", 1, AgentEventType.TURN_STARTED, Map.of("task", "分析项目")),
            AgentEvent.of("s1", "t1", 2, AgentEventType.ASSISTANT_DELTA, Map.of("delta", "我先查看项目结构。")),
            AgentEvent.of("s1", "t1", 3, AgentEventType.TOOL_STARTED, Map.of("toolId", "ls")),
            AgentEvent.of("s1", "t1", 4, AgentEventType.COMMAND_OUTPUT_DELTA, Map.of("stream", "stdout", "delta", "backend\nfrontend\n")),
            AgentEvent.of("s1", "t1", 5, AgentEventType.APPROVAL_REQUESTED, Map.of("risk", "HIGH", "reason", "需要修改文件", "summary", "写入 README.md")),
            AgentEvent.of("s1", "t1", 6, AgentEventType.TURN_COMPLETED, Map.of("status", "COMPLETED"))
        ));

        String output = String.join("\n", lines);
        assertTrue(output.contains("开始任务: 分析项目"));
        assertTrue(output.contains("我先查看项目结构。"));
        assertTrue(output.contains("工具: ls"));
        assertTrue(output.contains("stdout"));
        assertTrue(output.contains("需要审批"));
        assertTrue(output.contains("任务完成"));
    }
}
