package com.codingx.chat.interfaces.request;

import com.codingx.common.error.ErrorMessageCatalog;
import jakarta.validation.constraints.NotNull;

/**
 * 定义单个会话置顶请求载体。
 */
public record ConversationPinRequest(
    @NotNull(message = ErrorMessageCatalog.CHAT_CONVERSATION_PIN_STATE_REQUIRED) Boolean pinned
) {
}
