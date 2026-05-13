package com.codingx.backend.task.application.command;

import com.codingx.backend.task.domain.model.RuntimeType;

public record CreateTaskCommand(String title, String description, RuntimeType runtimeType, Long workspaceId) {
}