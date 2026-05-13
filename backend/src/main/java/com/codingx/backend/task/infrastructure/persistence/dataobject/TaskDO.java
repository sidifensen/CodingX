package com.codingx.backend.task.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("cx_task")
public class TaskDO {

    @TableId("id")
    private Long id;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("status")
    private String status;

    @TableField("runtime_type")
    private String runtimeType;

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("created_by")
    private Long createdBy;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("error_message")
    private String errorMessage;

    @TableField("summary")
    private String summary;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("deleted")
    private Integer deleted;
}