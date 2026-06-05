package com.codingx.cli.agent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 支持增量推送的 Agent 事件源，TUI 可通过该接口实时刷新后端 SSE 事件。
 */
public interface StreamingAgentEventSource extends AgentEventSource {

    /**
     * 启动一轮任务，并在事件到达时立即回调消费者。
     *
     * @param task 用户输入的任务内容。
     * @param workspace CLI 当前工作区路径。
     * @param eventConsumer 单个事件消费者，调用方负责把事件送入 UI 或测试收集器。
     */
    void startTurn(String task, Path workspace, Consumer<AgentEvent> eventConsumer);

    /**
     * 兼容同步事件源边界：收集增量事件后返回完整快照。
     *
     * @param task 用户输入的任务内容。
     * @param workspace CLI 当前工作区路径。
     * @return 本轮任务产生的事件快照。
     */
    @Override
    default List<AgentEvent> startTurn(String task, Path workspace) {
        List<AgentEvent> events = new ArrayList<>();
        startTurn(task, workspace, events::add);
        return events;
    }
}
