package com.codingx.backend.task.infrastructure.persistence.dataobject;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Maps the persistence fields used by TaskDO.
 */
@Data
@TableName("task")
public class TaskDO {

    @TableId("id")

    /**
     * Primary identifier.
     */
    private Long id;
    @TableField("title")

    /**
     * Display title.
     */
    private String title;
    @TableField("description")

    /**
     * Detailed description.
     */
    private String description;
    @TableField("status")

    /**
     * Current status value.
     */
    private String status;
    @TableField("runtime_type")

    /**
     * Runtime type.
     */
    private String runtimeType;
    @TableField("workspace_id")

    /**
     * Related workspace identifier.
     */
    private Long workspaceId;
    @TableField("created_by")

    /**
     * Creator user identifier.
     */
    private Long createdBy;
    @TableField("started_at")

    /**
     * Start timestamp.
     */
    private LocalDateTime startedAt;
    @TableField("finished_at")

    /**
     * Completion timestamp.
     */
    private LocalDateTime finishedAt;
    @TableField("error_message")

    /**
     * Error message.
     */
    private String errorMessage;
    @TableField("summary")

    /**
     * Summary text.
     */
    private String summary;
    @TableField("created_at")

    /**
     * Creation timestamp.
     */
    private LocalDateTime createdAt;
    @TableField("updated_at")

    /**
     * Last update timestamp.
     */
    private LocalDateTime updatedAt;
    @TableField("deleted")

    /**
     * Logical deletion flag.
     */
    private Integer deleted;
}
