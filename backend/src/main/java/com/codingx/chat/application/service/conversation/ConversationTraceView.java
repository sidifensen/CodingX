package com.codingx.chat.application.service;

import com.codingx.chat.domain.model.ChatTraceNode;
import com.codingx.chat.domain.model.ChatTraceRun;
import java.util.List;

/**
 * 定义 Trace 查询接口返回的聚合视图。
 */
public record ConversationTraceView(
    ChatTraceRun traceRun,
    List<ChatTraceNode> nodes
) {
}
