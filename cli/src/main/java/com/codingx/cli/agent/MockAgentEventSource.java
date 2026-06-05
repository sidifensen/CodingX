package com.codingx.cli.agent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 本地模拟 Agent 事件流，用于先把类似 Codex/MewCode 的终端体验跑起来。
 */
public class MockAgentEventSource implements AgentEventSource {

    @Override
    public List<AgentEvent> startTurn(String task, Path workspace) {
        String sessionId = "local-demo-session";
        String turnId = "local-demo-turn";
        List<AgentEvent> events = new ArrayList<>();

        // 步骤 1：先声明本地 demo 会话，让终端输出具备会话上下文。
        events.add(AgentEvent.of(sessionId, turnId, 1, AgentEventType.SESSION_STARTED, Map.of(
            "workspace", workspace.toString()
        )));
        events.add(AgentEvent.of(sessionId, turnId, 2, AgentEventType.TURN_STARTED, Map.of(
            "task", task
        )));

        // 步骤 2：模拟模型先解释行动，再发出工具/命令事件。
        events.add(AgentEvent.of(sessionId, turnId, 3, AgentEventType.ASSISTANT_DELTA, Map.of(
            "delta", "我会先查看当前仓库结构，再给出下一步建议。"
        )));
        events.add(AgentEvent.of(sessionId, turnId, 4, AgentEventType.TOOL_STARTED, Map.of(
            "toolId", "ls"
        )));
        events.add(AgentEvent.of(sessionId, turnId, 5, AgentEventType.COMMAND_OUTPUT_DELTA, Map.of(
            "stream", "stdout",
            "delta", "backend/\nfrontend/\ndocs/\ncli/\n"
        )));
        events.add(AgentEvent.of(sessionId, turnId, 6, AgentEventType.TOOL_COMPLETED, Map.of(
            "toolId", "ls"
        )));

        // 步骤 3：收束为可读结论，后续真实实现只替换事件源，不改渲染层。
        events.add(AgentEvent.of(sessionId, turnId, 7, AgentEventType.ASSISTANT_DELTA, Map.of(
            "delta", "基础终端已可接收 AgentEvent；下一步可以把事件源替换为后端 Agent API。"
        )));
        events.add(AgentEvent.of(sessionId, turnId, 8, AgentEventType.TURN_COMPLETED, Map.of(
            "status", "COMPLETED"
        )));
        return events;
    }
}
