package com.codingx.chat.interfaces.request;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 定义 SendChatMessageRequest 使用的数据载体。
 */
public record SendChatMessageRequest(
    @NotBlank(message = "content is required") String content, // 主体内容。
    List<Long> attachmentIds // 关联附件主键列表。
) {
}
