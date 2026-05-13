package com.codingx.backend.task.interfaces.request;
import com.codingx.backend.task.domain.model.RuntimeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Represents the request or response data carried by CreateTaskRequest.
 */
public record CreateTaskRequest(
    @NotBlank(message = "title is required") String title, // Display title.
    String description, // Detailed description.
    @NotNull(message = "runtimeType is required") RuntimeType runtimeType, // Runtime type.
    Long workspaceId // Related workspace identifier.
) {
}
