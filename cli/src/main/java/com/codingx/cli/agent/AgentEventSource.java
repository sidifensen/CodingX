package com.codingx.cli.agent;

import java.nio.file.Path;
import java.util.List;

/**
 * Agent 事件来源边界；MVP 使用本地 mock，后续可替换为后端 Agent API 客户端。
 */
public interface AgentEventSource {

    /**
     * 启动一轮任务并返回有序事件快照。
     *
     * @param task 用户输入的任务内容。
     * @param workspace CLI 当前工作区路径。
     * @return 用于终端渲染的 Agent 事件列表。
     */
    List<AgentEvent> startTurn(String task, Path workspace);
}
