package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 聊天目标步骤数据对象，映射目标下独立保存的步骤快照。
 */
@Data
@TableName("chat_goal_step")
public class ChatGoalStepDO {
    @TableId("id") private Long id; // 步骤主键 ID。
    @TableField("goal_id") private Long goalId; // 所属目标 ID。
    @TableField("step_key") private String stepKey; // 步骤稳定键，用于模型更新时识别同一业务步骤。
    @TableField("title") private String title; // 步骤标题。
    @TableField("status") private String status; // 步骤状态，PENDING/IN_PROGRESS/COMPLETED/BLOCKED/CANCELLED。
    @TableField("sort_no") private Integer sortNo; // 步骤排序号，升序展示。
    @TableField("detail") private String detail; // 步骤详情或阻塞原因，可为空。
    @TableField("started_at") private LocalDateTime startedAt; // 步骤开始时间，可为空。
    @TableField("completed_at") private LocalDateTime completedAt; // 步骤完成时间，可为空。
    @TableField("updated_at") private LocalDateTime updatedAt; // 步骤最近更新时间。
    @TableField("deleted") private Integer deleted; // 逻辑删除标记，0 正常，1 删除。
}
