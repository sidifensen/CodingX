package com.codingx.backend.artifact.domain.model;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Models the core domain state and behavior for TaskArtifact.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TaskArtifact {

    /**
     * Primary identifier.
     */
    private Long id;
    /**
     * Related task identifier.
     */
    private Long taskId;
    /**
     * artifactType value.
     */
    private String artifactType;
    /**
     * name value.
     */
    private String name;
    /**
     * Primary payload content.
     */
    private String content;
    /**
     * Storage path.
     */
    private String storagePath;
    /**
     * Creation timestamp.
     */
    private LocalDateTime createdAt;

    /**
     * Creates the data required by create and returns the result.
     * @param taskId input argument.
     * @param artifactType input argument.
     * @param name input argument.
     * @param content input argument.
     * @param storagePath input argument.
     * @return processing result.
     */
    public static TaskArtifact create(Long taskId, String artifactType, String name, String content, String storagePath) {
        if (taskId == null || StrUtil.hasBlank(artifactType, name)) {
            throw new IllegalArgumentException("Task artifact fields are required");
        }
        return TaskArtifact.builder()
            .id(IdUtil.getSnowflakeNextId())
            .taskId(taskId)
            .artifactType(artifactType)
            .name(name)
            .content(content)
            .storagePath(storagePath)
            .createdAt(LocalDateTime.now())
            .build();
    }
}
