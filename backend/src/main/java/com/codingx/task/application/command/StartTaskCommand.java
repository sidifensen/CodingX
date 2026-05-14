package com.codingx.task.application.command;

/**
 * 定义 StartTaskCommand 使用的数据载体。
 */
public record StartTaskCommand(
    Long taskId, // 关联任务标识。
    Long operatorId // operatorId 字段。
) {
}
