package com.codingx.chat.interfaces.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 定义消息反馈接口需要的请求载体。
 */
public record ChatMessageFeedbackRequest(
    @NotNull(message = "conversationId is required") Long conversationId,
    @NotNull(message = "vote is required") @Min(value = -1, message = "vote must be -1 or 1") @Max(value = 1, message = "vote must be -1 or 1") Integer vote,
    String reason,
    String comment
) {
}
