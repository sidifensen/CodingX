package com.codingx.automation.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 自动化任务表数据对象，映射用户手动或聊天创建的定时任务配置。
 */
@Data
@TableName("automation_task")
public class AutomationTaskDO {

    @TableId("id") private Long id; // 自动化任务主键。
    @TableField("user_id") private Long userId; // 任务归属用户 ID。
    @TableField("workspace_id") private Long workspaceId; // 任务归属工作空间 ID，可为空。
    @TableField("source_type") private String sourceType; // 创建来源，MANUAL 或 CHAT。
    @TableField("source_conversation_id") private Long sourceConversationId; // 来源会话 ID，手动任务可为空。
    @TableField("name") private String name; // 任务名称。
    @TableField("prompt") private String prompt; // 任务需求说明。
    @TableField("schedule_type") private String scheduleType; // 计划类型，DAILY、WEEKLY 或 ONCE。
    @TableField("schedule_time") private String scheduleTime; // 固定执行时间，格式 HH:mm。
    @TableField("schedule_day_of_week") private Integer scheduleDayOfWeek; // 每周执行星期，1 周一至 7 周日。
    @TableField("once_execute_at") private LocalDateTime onceExecuteAt; // 一次性任务执行时间。
    @TableField("next_run_at") private LocalDateTime nextRunAt; // 下一次计划触发时间。
    @TableField("last_run_at") private LocalDateTime lastRunAt; // 最近一次触发时间。
    @TableField("last_run_status") private String lastRunStatus; // 最近一次触发状态摘要。
    @TableField("enabled") private Integer enabled; // 启用状态，1 表示启用。
    @TableField("created_at") private LocalDateTime createdAt; // 记录创建时间。
    @TableField("updated_at") private LocalDateTime updatedAt; // 记录最近更新时间。
    @TableField("deleted") private Integer deleted; // 逻辑删除标记，1 表示已删除。
}
