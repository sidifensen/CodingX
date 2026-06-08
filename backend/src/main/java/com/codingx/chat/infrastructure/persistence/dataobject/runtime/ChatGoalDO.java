package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 聊天目标主表数据对象，映射 chat_goal 的会话级目标状态。
 */
@Data
@TableName("chat_goal")
public class ChatGoalDO {
    @TableId("id") private Long id; // 目标主键 ID。
    @TableField("conversation_id") private Long conversationId; // 所属会话 ID，查询 active goal 的第一过滤条件。
    @TableField("user_id") private Long userId; // 目标归属用户 ID，用于权限隔离。
    @TableField("goal_key") private String goalKey; // 模型传入的稳定目标键，默认 default。
    @TableField("title") private String title; // 目标标题，展示在目标浮窗。
    @TableField("description") private String description; // 目标说明，可为空。
    @TableField("status") private String status; // 目标状态，ACTIVE/COMPLETED/BLOCKED/CANCELLED。
    @TableField("progress_summary") private String progressSummary; // 模型更新的进度摘要，可为空。
    @TableField("created_run_id") private Long createdRunId; // 创建目标的聊天运行 ID。
    @TableField("updated_run_id") private Long updatedRunId; // 最近更新目标的聊天运行 ID。
    @TableField("created_at") private LocalDateTime createdAt; // 目标创建时间。
    @TableField("updated_at") private LocalDateTime updatedAt; // 目标最近更新时间。
    @TableField("completed_at") private LocalDateTime completedAt; // 目标进入终态时间，ACTIVE 时为空。
    @TableField("deleted") private Integer deleted; // 逻辑删除标记，0 正常，1 删除。
}
