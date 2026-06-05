package com.codingx.cli.tui;

import com.codingx.cli.agent.AgentEvent;
import com.williamcallahan.tui4j.compat.bubbletea.Message;

import java.util.List;

/**
 * 后台 Agent 流事件进入 tui4j 主更新循环的消息，避免后台线程直接修改 TUI 状态。
 *
 * @param events 本次推送的一批 Agent 事件，通常来自后端 SSE 的单个事件映射结果。
 */
public record AgentEventsMessage(List<AgentEvent> events) implements Message {
}
