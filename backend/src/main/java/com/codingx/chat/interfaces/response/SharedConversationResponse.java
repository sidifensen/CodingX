package com.codingx.chat.interfaces.response;

import java.util.List;

/**
 * 公开分享页返回会话元信息与消息回放列表。
 */
public record SharedConversationResponse(
    ChatConversationResponse conversation, // 分享会话元信息，已屏蔽写操作所需字段。
    List<ChatMessageResponse> messages // 分享范围内的消息回放列表，按原会话顺序返回。
) {
}
