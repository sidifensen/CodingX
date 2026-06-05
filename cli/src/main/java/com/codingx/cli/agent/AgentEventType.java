package com.codingx.cli.agent;

/**
 * CLI MVP 支持的 Agent 事件类型，命名映射后端统一事件协议。
 */
public enum AgentEventType {
    SESSION_STARTED("session.started"),
    TURN_STARTED("turn.started"),
    ASSISTANT_DELTA("assistant.delta"),
    THINKING_DELTA("thinking.delta"),
    TOOL_STARTED("tool.started"),
    TOOL_OUTPUT_DELTA("tool.output.delta"),
    TOOL_COMPLETED("tool.completed"),
    COMMAND_STARTED("command.started"),
    COMMAND_OUTPUT_DELTA("command.output.delta"),
    COMMAND_COMPLETED("command.completed"),
    FILE_DIFF("file.diff"),
    APPROVAL_REQUESTED("approval.requested"),
    APPROVAL_RESOLVED("approval.resolved"),
    TURN_COMPLETED("turn.completed"),
    TURN_INTERRUPTED("turn.interrupted"),
    ERROR("error"),
    UNKNOWN("unknown");

    /**
     * 与后端统一事件协议对应的传输名称。
     */
    private final String wireName;

    AgentEventType(String wireName) {
        this.wireName = wireName;
    }

    /**
     * @return 事件传输名称。
     */
    public String wireName() {
        return wireName;
    }
}
