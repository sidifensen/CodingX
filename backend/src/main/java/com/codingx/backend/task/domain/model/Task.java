package com.codingx.backend.task.domain.model;

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
public class Task {

    private Long id;
    private String title;
    private String description;
    private TaskStatus status;
    private RuntimeType runtimeType;
    private Long workspaceId;
    private Long createdBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
    private String summary;

    public static Task create(Long id, String title, String description, RuntimeType runtimeType, Long workspaceId, Long createdBy) {
        if (id == null || createdBy == null) {
            throw new IllegalArgumentException("Task id and createdBy are required");
        }
        if (StrUtil.isBlank(title) || runtimeType == null) {
            throw new IllegalArgumentException("Task title and runtime type are required");
        }
        return Task.builder()
            .id(id)
            .title(title)
            .description(description)
            .status(TaskStatus.CREATED)
            .runtimeType(runtimeType)
            .workspaceId(workspaceId)
            .createdBy(createdBy)
            .build();
    }

    public void start() {
        ensureStatus(TaskStatus.CREATED, "Only created tasks can start");
        this.status = TaskStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void complete(String summary) {
        ensureStatus(TaskStatus.RUNNING, "Only running tasks can complete");
        this.status = TaskStatus.SUCCEEDED;
        this.summary = summary;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void fail(String errorMessage) {
        ensureStatus(TaskStatus.RUNNING, "Only running tasks can fail");
        this.status = TaskStatus.FAILED;
        this.errorMessage = StrUtil.blankToDefault(errorMessage, "Unknown error");
        this.finishedAt = LocalDateTime.now();
    }

    public void updateSummary(String summary) {
        this.summary = summary;
    }

    private void ensureStatus(TaskStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }
}