package com.codingx.backend.task.domain.model;
import cn.hutool.core.util.StrUtil;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Models the core domain state and behavior for Task.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Task {

    /**
     * Primary identifier.
     */
    private Long id;
    /**
     * Display title.
     */
    private String title;
    /**
     * Detailed description.
     */
    private String description;
    /**
     * Current status value.
     */
    private TaskStatus status;
    /**
     * Runtime type.
     */
    private RuntimeType runtimeType;
    /**
     * Related workspace identifier.
     */
    private Long workspaceId;
    /**
     * Creator user identifier.
     */
    private Long createdBy;
    /**
     * Start timestamp.
     */
    private LocalDateTime startedAt;
    /**
     * Completion timestamp.
     */
    private LocalDateTime finishedAt;
    /**
     * Error message.
     */
    private String errorMessage;
    /**
     * Summary text.
     */
    private String summary;

    /**
     * Creates the data required by create and returns the result.
     * @param id input argument.
     * @param title input argument.
     * @param description input argument.
     * @param runtimeType input argument.
     * @param workspaceId input argument.
     * @param createdBy input argument.
     * @return processing result.
     */
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

    /**
     * Starts the workflow handled by start.
     */
    public void start() {
        ensureStatus(TaskStatus.CREATED, "Only created tasks can start");
        this.status = TaskStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    /**
     * Marks the workflow handled by complete as completed.
     * @param summary input argument.
     */
    public void complete(String summary) {
        ensureStatus(TaskStatus.RUNNING, "Only running tasks can complete");
        this.status = TaskStatus.SUCCEEDED;
        this.summary = summary;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    /**
     * Marks the workflow handled by fail as failed.
     * @param errorMessage input argument.
     */
    public void fail(String errorMessage) {
        ensureStatus(TaskStatus.RUNNING, "Only running tasks can fail");
        this.status = TaskStatus.FAILED;
        this.errorMessage = StrUtil.blankToDefault(errorMessage, "Unknown error");
        this.finishedAt = LocalDateTime.now();
    }

    /**
     * Updates the state handled by updateSummary.
     * @param summary input argument.
     */
    public void updateSummary(String summary) {
        this.summary = summary;
    }

    /**
     * Ensures the preconditions required by ensureStatus.
     * @param expected input argument.
     * @param message input argument.
     */
    private void ensureStatus(TaskStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }
}
