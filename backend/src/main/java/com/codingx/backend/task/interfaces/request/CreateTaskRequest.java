package com.codingx.backend.task.interfaces.request;

import com.codingx.backend.task.domain.model.RuntimeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTaskRequest(
    @NotBlank(message = "title is required") String title,
    String description,
    @NotNull(message = "runtimeType is required") RuntimeType runtimeType,
    Long workspaceId
) {
}