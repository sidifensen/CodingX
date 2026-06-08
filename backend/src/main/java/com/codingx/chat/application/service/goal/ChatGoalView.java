package com.codingx.chat.application.service.goal;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天目标视图，作为目标工具 metadata、SSE goal 事件和查询接口的统一应用层快照。
 *
 * @param id 目标 ID 字符串，避免前端 Long 精度丢失。
 * @param conversationId 会话 ID 字符串。
 * @param goalKey 目标稳定键。
 * @param title 目标标题。
 * @param description 目标说明，可为空。
 * @param status 目标状态。
 * @param progressSummary 当前进度摘要，可为空。
 * @param eventType 最近一次触发该视图的事件类型，可为空。
 * @param createdAt 目标创建时间。
 * @param updatedAt 目标更新时间。
 * @param completedAt 目标完成或终态时间，可为空。
 * @param steps 目标步骤快照。
 */
public record ChatGoalView(
    String id,
    String conversationId,
    String goalKey,
    String title,
    String description,
    String status,
    String progressSummary,
    String eventType,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime completedAt,
    List<StepView> steps
) {

    /**
     * 聊天目标步骤视图。
     *
     * @param id 步骤 ID 字符串。
     * @param stepKey 步骤稳定键。
     * @param title 步骤标题。
     * @param status 步骤状态。
     * @param detail 步骤详情或阻塞原因，可为空。
     * @param sortNo 步骤排序号。
     */
    public record StepView(
        String id,
        String stepKey,
        String title,
        String status,
        String detail,
        Integer sortNo
    ) {
    }
}
