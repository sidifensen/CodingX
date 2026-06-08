package com.codingx.automation.domain.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 自动化定时任务领域对象，承载用户计划、来源会话和最近调度状态。
 */
@Getter
@Builder(toBuilder = true)
public class AutomationTask {

    /** 任务主键。 */
    private Long id;
    /** 任务归属用户，所有查询和变更都必须按该字段隔离。 */
    private Long userId;
    /** 任务归属工作空间，可为空，聊天创建时优先来自来源会话。 */
    private Long workspaceId;
    /** 创建来源，区分页面手动创建和聊天自动创建。 */
    private AutomationTaskSourceType sourceType;
    /** 来源会话 ID；手动创建可为空，聊天创建必须写入。 */
    private Long sourceConversationId;
    /** 任务展示名称。 */
    private String name;
    /** 任务需求说明，调度执行时作为后续任务输入。 */
    private String prompt;
    /** 计划类型，第一版支持每日、每周和一次性。 */
    private AutomationScheduleType scheduleType;
    /** 每日或每周任务的执行时间，格式 HH:mm。 */
    private String scheduleTime;
    /** 每周任务的执行星期，1 表示周一，7 表示周日。 */
    private Integer scheduleDayOfWeek;
    /** 一次性任务的执行时间。 */
    private LocalDateTime onceExecuteAt;
    /** 下一次应触发的时间，调度器以该字段筛选到期任务。 */
    private LocalDateTime nextRunAt;
    /** 最近一次触发时间。 */
    private LocalDateTime lastRunAt;
    /** 最近一次触发状态摘要，例如 TRIGGERED 或 FAILED。 */
    private String lastRunStatus;
    /** 启用状态，false 时调度器跳过。 */
    private boolean enabled;
    /** 逻辑删除状态，true 时不再对用户展示或调度。 */
    private boolean deleted;
    /** 记录创建时间。 */
    private LocalDateTime createdAt;
    /** 记录最近更新时间。 */
    private LocalDateTime updatedAt;
}
