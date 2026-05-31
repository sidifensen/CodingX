package com.codingx.task.application.command;

import com.codingx.task.domain.model.RuntimeType;
import java.util.List;

/**
 * 创建任务的应用层命令，承接任务接口请求并传递给任务聚合创建流程。
 *
 * @param title 已通过接口层校验的任务标题，不能为空。
 * @param description 任务说明，可为空；用于补充任务目标和执行背景。
 * @param runtimeType 任务运行时类型，决定选择本地、云端或模拟执行器。
 * @param workspaceId 关联工作空间标识，可为空；非空时服务层校验工作空间存在。
 * @param skillCodes 创建任务时绑定的技能编码列表，可为空；领域对象会按空列表兜底。
 */
public record CreateTaskCommand(
    String title, // 已通过接口层校验的任务标题。
    String description, // 任务说明，可为空。
    RuntimeType runtimeType, // 任务运行时类型，决定执行器选择。
    Long workspaceId, // 关联工作空间标识，可为空。
    List<String> skillCodes // 创建任务时绑定的技能编码列表，可为空。
) {
}
