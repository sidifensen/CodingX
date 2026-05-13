package com.codingx.backend.task.application.command;

/**
 * Represents the request or response data carried by StartTaskCommand.
 */
public record StartTaskCommand(
    Long taskId, // Related task identifier.
    Long operatorId // operatorId value.
) {
}
