package com.codingx.chat.interfaces.request;

import java.util.List;

/**
 * 删除会话内消息的请求体。
 * @param messageIds 待删除消息主键列表。
 */
public record DeleteChatMessagesRequest(
    List<Long> messageIds // 前端选择删除的消息主键列表，可为空；服务层会过滤空值和重复值，空列表时不执行删除。
) {
}
