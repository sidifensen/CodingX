package com.codingx.automation.interfaces.response;

import java.time.LocalDateTime;

/**
 * 用户端自动化任务响应，前端列表和创建结果共用。
 *
 * @param id 任务主键。
 * @param name 任务名称。
 * @param prompt 任务需求说明。
 * @param sourceType 创建来源。
 * @param sourceConversationId 来源会话 ID。
 * @param scheduleType 计划类型。
 * @param scheduleTime 固定执行时间。
 * @param scheduleDayOfWeek 每周执行星期。
 * @param onceExecuteAt 一次性执行时间。
 * @param nextRunAt 下一次计划触发时间。
 * @param lastRunAt 最近触发时间。
 * @param lastRunStatus 最近触发状态。
 * @param enabled 是否启用。
 * @param workspaceId 工作空间 ID。
 */
public record AutomationTaskResponse(
    Long id,
    String name,
    String prompt,
    String sourceType,
    Long sourceConversationId,
    String scheduleType,
    String scheduleTime,
    Integer scheduleDayOfWeek,
    LocalDateTime onceExecuteAt,
    LocalDateTime nextRunAt,
    LocalDateTime lastRunAt,
    String lastRunStatus,
    Boolean enabled,
    Long workspaceId
) {
}
