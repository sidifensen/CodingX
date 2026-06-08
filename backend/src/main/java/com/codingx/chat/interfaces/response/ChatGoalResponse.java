package com.codingx.chat.interfaces.response;

import com.codingx.chat.application.service.goal.ChatGoalView;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天目标接口响应，所有 ID 使用字符串，避免前端读取 Snowflake Long 时精度丢失。
 *
 * @param id 目标 ID 字符串。
 * @param conversationId 会话 ID 字符串。
 * @param goalKey 目标稳定键。
 * @param title 目标标题。
 * @param description 目标说明，可为空。
 * @param status 目标状态。
 * @param progressSummary 当前进度摘要，可为空。
 * @param eventType 最近事件类型，可为空。
 * @param createdAt 创建时间。
 * @param updatedAt 更新时间。
 * @param completedAt 完成或终态时间，可为空。
 * @param steps 步骤快照。
 */
public record ChatGoalResponse(
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
    List<StepResponse> steps
) {

    /**
     * 从应用层目标视图转换为接口响应。
     *
     * @param view 应用层目标视图。
     * @return 接口响应。
     */
    public static ChatGoalResponse from(ChatGoalView view) {
        return new ChatGoalResponse(
            view.id(),
            view.conversationId(),
            view.goalKey(),
            view.title(),
            view.description(),
            view.status(),
            view.progressSummary(),
            view.eventType(),
            view.createdAt(),
            view.updatedAt(),
            view.completedAt(),
            view.steps().stream().map(StepResponse::from).toList()
        );
    }

    /**
     * 目标步骤接口响应。
     *
     * @param id 步骤 ID 字符串。
     * @param stepKey 步骤稳定键。
     * @param title 步骤标题。
     * @param status 步骤状态。
     * @param detail 步骤详情或阻塞原因，可为空。
     * @param sortNo 步骤排序号。
     */
    public record StepResponse(
        String id,
        String stepKey,
        String title,
        String status,
        String detail,
        Integer sortNo
    ) {

        /**
         * 从应用层步骤视图转换为接口响应。
         *
         * @param stepView 应用层步骤视图。
         * @return 步骤接口响应。
         */
        public static StepResponse from(ChatGoalView.StepView stepView) {
            return new StepResponse(
                stepView.id(),
                stepView.stepKey(),
                stepView.title(),
                stepView.status(),
                stepView.detail(),
                stepView.sortNo()
            );
        }
    }
}
