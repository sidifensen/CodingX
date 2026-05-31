package com.codingx.task.application.command;

/**
 * 启动任务的应用层命令，封装待启动任务和当前操作者身份。
 *
 * @param taskId 待启动任务标识，不能为空；服务层按该值加载任务聚合。
 * @param operatorId 当前操作用户标识，用于校验任务归属和执行权限。
 */
public record StartTaskCommand(
    Long taskId, // 待启动任务标识。
    Long operatorId // 当前操作用户标识，用于校验任务归属。
) {
}
