package com.codingx.backend.task.interfaces.response;

import com.codingx.backend.task.domain.model.RuntimeType;
import com.codingx.backend.task.domain.model.TaskStatus;

public record TaskResponse(
    Long id,
    String title,
    String description,
    TaskStatus status,
    RuntimeType runtimeType,
    Long workspaceId,
    Long createdBy,
    String summary,
    String errorMessage
) {
}