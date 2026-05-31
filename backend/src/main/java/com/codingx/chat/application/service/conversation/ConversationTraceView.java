package com.codingx.chat.application.service;

import com.codingx.chat.domain.model.ChatTraceNode;
import com.codingx.chat.domain.model.ChatTraceRun;
import java.util.List;

/**
 * Trace 查询接口返回的聚合视图，聚合根链路和节点明细供前端渲染树形链路。
 *
 * @param traceRun Trace 根运行记录，包含状态、耗时和入口信息。
 * @param nodes Trace 节点列表，按查询服务规范化后的顺序返回，可为空列表。
 */
public record ConversationTraceView(
    ChatTraceRun traceRun, // Trace 根运行记录，包含状态、耗时和入口信息。
    List<ChatTraceNode> nodes // Trace 节点列表，按查询服务规范化后的顺序返回，可为空列表。
) {
}
