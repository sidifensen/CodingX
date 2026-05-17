package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义任务技能绑定表的数据对象映射。
 * 任务技能绑定与任务 MCP 绑定独立存储，避免运行时上下文串扰。
 */
@Data
@TableName("task_skill")
public class TaskSkillDO {

    @TableId("id")
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("skill_code")
    private String skillCode;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
