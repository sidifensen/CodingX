package com.codingx.backend.artifact.domain.model;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TaskArtifact {

    private Long id;
    private Long taskId;
    private String artifactType;
    private String name;
    private String content;
    private String storagePath;
    private LocalDateTime createdAt;

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