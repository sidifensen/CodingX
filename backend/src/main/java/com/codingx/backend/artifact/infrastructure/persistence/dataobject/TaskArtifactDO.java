package com.codingx.backend.artifact.infrastructure.persistence.dataobject;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Maps the persistence fields used by TaskArtifactDO.
 */
@Data
@TableName("task_artifact")
public class TaskArtifactDO {
    @TableId("id") private Long id;
    @TableField("task_id") private Long taskId;
    @TableField("artifact_type") private String artifactType;
    @TableField("name") private String name;
    @TableField("content") private String content;
    @TableField("storage_path") private String storagePath;
    @TableField("created_at") private LocalDateTime createdAt;
}
