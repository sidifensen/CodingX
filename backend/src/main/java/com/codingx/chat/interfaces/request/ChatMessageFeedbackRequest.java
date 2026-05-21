package com.codingx.chat.interfaces.request;

import com.codingx.common.error.ErrorMessageCatalog;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 定义消息反馈接口需要的请求载体。
 */
public record ChatMessageFeedbackRequest(
    @NotNull(message = ErrorMessageCatalog.CHAT_FEEDBACK_CONVERSATION_ID_REQUIRED) Long conversationId,
    @NotNull(message = ErrorMessageCatalog.CHAT_FEEDBACK_VOTE_REQUIRED) @Min(value = -1, message = ErrorMessageCatalog.CHAT_FEEDBACK_VOTE_INVALID) @Max(value = 1, message = ErrorMessageCatalog.CHAT_FEEDBACK_VOTE_INVALID) Integer vote,
    String reason,
    String comment
) {
}
