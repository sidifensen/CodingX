package com.codingx.artifact.domain.model;
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
     * 创建 create 所需数据并返回结果。
     * @param taskId 输入参数。
     * @param artifactType 输入参数。
     * @param name 输入参数。
     * @param content 输入参数。
     * @param storagePath 输入参数。
     * @return 输入参数。
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
}
