package com.codingx.backend.task.application.command;
import com.codingx.backend.task.domain.model.RuntimeType;

/**
 * Represents the request or response data carried by CreateTaskCommand.
 */
public record CreateTaskCommand(
    String title, // Display title.
    String description, // Detailed description.
    RuntimeType runtimeType, // Runtime type.
    Long workspaceId // Related workspace identifier.
) {
}
