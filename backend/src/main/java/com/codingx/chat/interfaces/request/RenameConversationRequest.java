package com.codingx.chat.interfaces.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 定义会话重命名请求载体。
 */
public record RenameConversationRequest(
    @NotBlank(message = "title is required") String title
) {
}
