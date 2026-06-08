package com.codingx.automation.interfaces.request;

import java.time.LocalDateTime;

/**
 * 用户端手动创建自动化任务请求。
 *
 * @param name 任务名称。
 * @param prompt 任务需求说明。
 * @param scheduleType 计划类型，DAILY、WEEKLY 或 ONCE。
 * @param scheduleTime 固定执行时间，格式 HH:mm。
 * @param scheduleDayOfWeek 每周执行星期，1 周一至 7 周日。
 * @param onceExecuteAt 一次性任务执行时间。
 * @param workspaceId 任务归属工作空间，可为空。
 */
public record AutomationTaskCreateRequest(
    String name,
    String prompt,
    String scheduleType,
    String scheduleTime,
    Integer scheduleDayOfWeek,
    LocalDateTime onceExecuteAt,
    Long workspaceId
) {
}
