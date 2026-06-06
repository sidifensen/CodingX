package com.codingx.cli.agent;

import java.nio.file.Path;
import java.util.List;

/**
 * Agent 事件来源边界；生产环境由后端聊天流客户端实现，测试可继续使用本地 mock。
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

    /**
     * 启动一轮任务并携带当前计划模式。
     * <p>
     * 兼容约束：旧事件源不理解 planMode 时仍按普通聊天执行，生产后端事件源会覆盖该方法透传给后端。
     *
     * @param task 用户输入的任务内容。
     * @param workspace CLI 当前工作区路径。
     * @param planMode true 表示请求后端按规划模式处理。
     * @return 用于终端渲染的 Agent 事件列表。
     */
    default List<AgentEvent> startTurn(String task, Path workspace, boolean planMode) {
        return startTurn(task, workspace);
    }
}
