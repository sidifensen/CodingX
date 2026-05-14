package com.codingx.task.infrastructure.persistence.dataobject;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义 TaskDO 的数据库字段映射。
 */
@Data
@TableName("task")
public class TaskDO {

    @TableId("id")

    /**
     * 主键标识。
     */
    private Long id;
    @TableField("title")

    /**
     * 展示标题。
     */
    private String title;
    @TableField("description")

    /**
     * 详细描述。
     */
    private String description;
    @TableField("status")

    /**
     * 当前状态值。
     */
    private String status;
    @TableField("runtime_type")

    /**
     * 运行时类型。
     */
    private String runtimeType;
    @TableField("workspace_id")

    /**
     * 关联工作区标识。
     */
    private Long workspaceId;
    @TableField("created_by")

    /**
     * 创建人用户标识。
     */
    private Long createdBy;
    @TableField("started_at")

    /**
     * 开始时间。
     */
    private LocalDateTime startedAt;
    @TableField("finished_at")

    /**
     * 完成时间。
     */
    private LocalDateTime finishedAt;
    @TableField("error_message")

    /**
     * 错误信息。
     */
    private String errorMessage;
    @TableField("summary")

    /**
     * 摘要内容。
     */
    private String summary;
    @TableField("created_at")

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    @TableField("updated_at")

    /**
     * 最后更新时间。
     */
    private LocalDateTime updatedAt;
    @TableField("deleted")

    /**
     * 逻辑删除标记。
     */
    private Integer deleted;
}
