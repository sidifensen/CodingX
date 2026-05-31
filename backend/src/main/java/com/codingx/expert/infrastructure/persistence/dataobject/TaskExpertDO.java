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

    /**
     * 任务专家绑定主键。
     */
    @TableId("id")
    private Long id;

    /**
     * 关联任务主键。
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 专家编码，对应聊天专家配置中的 expert_code。
     */
    @TableField("expert_code")
    private String expertCode;

    /**
     * 绑定创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
