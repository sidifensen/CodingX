package com.codingx.chat.interfaces.request;

import java.util.List;

/**
 * 分享会话请求，messageIds 为空时表示分享整段会话。
 * @param messageIds 前端选择的消息标识列表。
 */
public record ShareConversationRequest(
    List<Long> messageIds // 前端选择公开分享的消息主键列表，可为空；为空时公开整段会话。
) {
}
