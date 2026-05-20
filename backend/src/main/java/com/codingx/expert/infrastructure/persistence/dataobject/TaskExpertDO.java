package com.codingx.expert.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义任务专家绑定表的数据对象映射。
 */
@Data
@TableName("task_expert")
public class TaskExpertDO {

    @TableId("id")
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("expert_code")
    private String expertCode;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
