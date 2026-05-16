package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义任务技能绑定表的数据对象映射。
 * 当前沿用存量 `task_mcp` 物理表，避免迁移期间中断线上数据。
 */
@Data
@TableName("task_mcp")
public class TaskSkillDO {

    @TableId("id")
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("mcp_code")
    private String skillCode;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
