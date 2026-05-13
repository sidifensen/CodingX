package com.codingx.backend.task.interfaces.response;
import com.codingx.backend.task.domain.model.RuntimeType;
import com.codingx.backend.task.domain.model.TaskStatus;

/**
 * Represents the request or response data carried by TaskResponse.
 */
public record TaskResponse(
    Long id, // Primary identifier.
    String title, // Display title.
    String description, // Detailed description.
    TaskStatus status, // Current status value.
    RuntimeType runtimeType, // Runtime type.
    Long workspaceId, // Related workspace identifier.
    Long createdBy, // Creator user identifier.
    String summary, // Summary text.
    String errorMessage // Error message.
) {
}
