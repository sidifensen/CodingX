package com.codingx.chat.interfaces.request;

import java.util.List;

/**
 * 删除会话内消息的请求体。
 * @param messageIds 待删除消息主键列表。
 */
public record DeleteChatMessagesRequest(
    List<Long> messageIds
) {
}
