package com.codingx.chat.interfaces.response;

import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 定义反馈列表项响应结构，供管理端分页表格直接展示。
 * @param id 反馈主键。
 * @param messageId 关联消息主键。
 * @param conversationId 关联会话主键。
 * @param userId 提交反馈的用户主键。
 * @param vote 投票值（1 点赞，-1 点踩）。
 * @param reason 反馈原因。
 * @param comment 反馈补充说明。
 * @param createdAt 创建时间。
 * @param updatedAt 更新时间。
 */
@Builder
public record AdminChatMessageFeedbackListItemResponse(
    Long id,
    Long messageId,
    Long conversationId,
    Long userId,
    Integer vote,
    String reason,
    String comment,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}

