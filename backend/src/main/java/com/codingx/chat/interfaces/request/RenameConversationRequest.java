package com.codingx.chat.interfaces.request;

import com.codingx.common.error.ErrorMessageCatalog;
import jakarta.validation.constraints.NotBlank;

/**
 * 定义会话重命名请求载体。
 */
public record RenameConversationRequest(
    @NotBlank(message = ErrorMessageCatalog.CHAT_CONVERSATION_TITLE_REQUIRED) String title
) {
}
