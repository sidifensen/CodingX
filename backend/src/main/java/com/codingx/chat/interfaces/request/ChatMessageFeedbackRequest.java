package com.codingx.chat.interfaces.request;

import com.codingx.common.error.ErrorMessageCatalog;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 消息反馈接口请求体，承载点赞、点踩、清空反馈以及原因补充信息。
 *
 * @param conversationId 被反馈消息所属会话主键，用于服务层校验消息归属与当前用户权限。
 * @param vote 反馈投票值，1 表示点赞，-1 表示点踩，0 用于清空或中性反馈。
 * @param reason 前端选择的反馈原因编码或短文本，可为空。
 * @param comment 用户补充的自由文本说明，可为空。
 */
public record ChatMessageFeedbackRequest(
    @NotNull(message = ErrorMessageCatalog.CHAT_FEEDBACK_CONVERSATION_ID_REQUIRED) Long conversationId, // 被反馈消息所属会话主键，用于服务层校验消息归属与当前用户权限。
    @NotNull(message = ErrorMessageCatalog.CHAT_FEEDBACK_VOTE_REQUIRED) @Min(value = -1, message = ErrorMessageCatalog.CHAT_FEEDBACK_VOTE_INVALID) @Max(value = 1, message = ErrorMessageCatalog.CHAT_FEEDBACK_VOTE_INVALID) Integer vote, // 反馈投票值，1 表示点赞，-1 表示点踩，0 用于清空或中性反馈。
    String reason, // 前端选择的反馈原因编码或短文本，可为空。
    String comment // 用户补充的自由文本说明，可为空。
) {
}
