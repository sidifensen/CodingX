package com.codingx.chat.interfaces.response;

import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 定义反馈详情响应结构，聚合反馈本体与消息/会话上下文。
 * @param id 反馈主键。
 * @param messageId 关联消息主键。
 * @param conversationId 关联会话主键。
 * @param conversationTitle 会话标题。
 * @param messageRole 消息角色。
 * @param messageContent 消息内容。
 * @param userId 用户主键。
 * @param vote 投票值（1 点赞，-1 点踩）。
 * @param reason 反馈原因。
 * @param comment 反馈补充说明。
 * @param createdAt 创建时间。
 * @param updatedAt 更新时间。
 */
@Builder
public record AdminChatMessageFeedbackDetailResponse(
    Long id,
    Long messageId,
    Long conversationId,
    String conversationTitle,
    String messageRole,
    String messageContent,
    Long userId,
    Integer vote,
    String reason,
    String comment,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}

