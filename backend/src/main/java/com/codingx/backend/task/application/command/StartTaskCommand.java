package com.codingx.backend.task.application.command;

public record StartTaskCommand(Long taskId, Long operatorId) {
}