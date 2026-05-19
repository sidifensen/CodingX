package com.codingx.chat.interfaces.response;

import com.codingx.chat.domain.model.ChatConversationStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

/**
 * 定义管理端会话详情响应结构。
 * @param id 会话主键。
 * @param title 会话标题。
 * @param createdBy 创建人用户 ID。
 * @param status 会话状态。
 * @param statusLabel 会话状态中文文案。
 * @param lastMessageAt 最近消息时间。
 * @param lastRunId 最近一次执行记录 ID。
 * @param createdAt 创建时间。
 * @param updatedAt 更新时间。
 * @param messages 会话消息列表。
 */
@Builder
public record AdminChatConversationDetailResponse(
    Long id,
    String title,
    Long createdBy,
    ChatConversationStatus status,
    String statusLabel,
    LocalDateTime lastMessageAt,
    Long lastRunId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<ChatMessageResponse> messages
) {
}
