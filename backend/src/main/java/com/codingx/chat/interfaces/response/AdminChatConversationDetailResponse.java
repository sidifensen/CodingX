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
 * @param goals 会话目标记录列表，管理端只读展示 chat_goal、chat_goal_step 和 chat_goal_event。
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
    List<ChatMessageResponse> messages,
    List<AdminChatGoalRecordResponse> goals
) {

    /**
     * 管理端目标主表响应，字段直接对应 chat_goal，用于排查目标工具运行状态。
     *
     * @param id 目标 ID，字符串化避免前端 Long 精度丢失。
     * @param conversationId 所属会话 ID，字符串化避免前端 Long 精度丢失。
     * @param userId 目标归属用户 ID，字符串化避免前端 Long 精度丢失。
     * @param goalKey 目标稳定键。
     * @param title 目标标题。
     * @param description 目标说明，可为空。
     * @param status 目标状态。
     * @param progressSummary 目标进度摘要，可为空。
     * @param createdRunId 创建目标的运行 ID，字符串化后可为空。
     * @param updatedRunId 最近更新目标的运行 ID，字符串化后可为空。
     * @param createdAt 创建时间。
     * @param updatedAt 更新时间。
     * @param completedAt 终态时间，ACTIVE 目标为空。
     * @param steps 目标步骤快照，对应 chat_goal_step。
     * @param events 目标事件流水，对应 chat_goal_event。
     */
    public record AdminChatGoalRecordResponse(
        String id,
        String conversationId,
        String userId,
        String goalKey,
        String title,
        String description,
        String status,
        String progressSummary,
        String createdRunId,
        String updatedRunId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt,
        List<AdminChatGoalStepResponse> steps,
        List<AdminChatGoalEventResponse> events
    ) {
    }

    /**
     * 管理端目标步骤响应，字段直接对应 chat_goal_step 的未删除步骤快照。
     *
     * @param id 步骤 ID，字符串化避免前端 Long 精度丢失。
     * @param goalId 所属目标 ID，字符串化避免前端 Long 精度丢失。
     * @param stepKey 步骤稳定键。
     * @param title 步骤标题。
     * @param status 步骤状态。
     * @param sortNo 步骤排序号。
     * @param detail 步骤详情或阻塞原因，可为空。
     * @param startedAt 步骤开始时间，可为空。
     * @param completedAt 步骤完成时间，可为空。
     * @param updatedAt 步骤更新时间。
     */
    public record AdminChatGoalStepResponse(
        String id,
        String goalId,
        String stepKey,
        String title,
        String status,
        Integer sortNo,
        String detail,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime updatedAt
    ) {
    }

    /**
     * 管理端目标事件响应，字段直接对应 chat_goal_event 的追加流水。
     *
     * @param id 事件 ID，字符串化避免前端 Long 精度丢失。
     * @param goalId 所属目标 ID，字符串化避免前端 Long 精度丢失。
     * @param conversationId 所属会话 ID，字符串化避免前端 Long 精度丢失。
     * @param runId 触发事件的运行 ID，字符串化后可为空。
     * @param eventType 事件类型。
     * @param payloadJson 事件载荷 JSON 原文，可为空。
     * @param createdAt 事件创建时间。
     */
    public record AdminChatGoalEventResponse(
        String id,
        String goalId,
        String conversationId,
        String runId,
        String eventType,
        String payloadJson,
        LocalDateTime createdAt
    ) {
    }
}
