package com.codingx.task.application.command;

/**
 * 启动任务的应用层命令。
 */
public record StartTaskCommand(
    Long taskId, // 待启动任务标识。
    Long operatorId // 当前操作用户标识，用于校验任务归属。
) {
}
