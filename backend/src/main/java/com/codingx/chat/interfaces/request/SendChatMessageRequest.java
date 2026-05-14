package com.codingx.chat.interfaces.request;
import jakarta.validation.constraints.NotBlank;

/**
 * 定义 SendChatMessageRequest 使用的数据载体。
 */
public record SendChatMessageRequest(
    @NotBlank(message = "content is required") String content // 主体内容。
) {
}
