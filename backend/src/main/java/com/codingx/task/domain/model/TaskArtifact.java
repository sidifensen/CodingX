package com.codingx.task.domain.model;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.common.error.ErrorMessageCatalog;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 定义 TaskArtifact 的核心领域状态与行为。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TaskArtifact {

    /**
     * 主键标识。
     */
    private Long id;

    /**
     * 关联任务标识。
     */
    private Long taskId;

    /**
     * artifactType 字段。
     */
    private String artifactType;

    /**
     * name 字段。
     */
    private String name;

    /**
     * 主体内容。
     */
    private String content;

    /**
     * 存储路径。
     */
    private String storagePath;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 创建新的任务产物，适用于运行时首次生成写入数据库的场景。
     * @param taskId 所属任务 ID。
     * @param artifactType 产物类型。
     * @param name 产物名称。
     * @param content 产物内容。
     * @param storagePath 产物存储路径。
     * @return 新建的任务产物。
     */
    public static TaskArtifact create(Long taskId, String artifactType, String name, String content, String storagePath) {
        if (taskId == null || StrUtil.hasBlank(artifactType, name)) {
            throw new IllegalArgumentException(ErrorMessageCatalog.TASK_ARTIFACT_FIELDS_REQUIRED);
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

    /**
     * 从数据库记录恢复任务产物，避免读库时重新生成主键和创建时间。
     * @param id 主键。
     * @param taskId 所属任务 ID。
     * @param artifactType 产物类型。
     * @param name 产物名称。
     * @param content 产物内容。
     * @param storagePath 产物存储路径。
     * @param createdAt 创建时间。
     * @return 恢复后的任务产物。
     */
    public static TaskArtifact restore(Long id, Long taskId, String artifactType, String name, String content, String storagePath, LocalDateTime createdAt) {
        return TaskArtifact.builder()
            .id(id)
            .taskId(taskId)
            .artifactType(artifactType)
            .name(name)
            .content(content)
            .storagePath(storagePath)
            .createdAt(createdAt)
            .build();
    }
}
