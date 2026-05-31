package com.codingx.task.application.command;
import com.codingx.task.domain.model.RuntimeType;
import java.util.List;

/**
 * 创建任务的应用层命令。
 */
public record CreateTaskCommand(
    String title, // 已通过接口层校验的任务标题。
    String description, // 任务说明，可为空。
    RuntimeType runtimeType, // 任务运行时类型，决定执行器选择。
    Long workspaceId, // 关联工作空间标识，可为空。
    List<String> skillCodes // 创建任务时绑定的技能编码列表，可为空。
) {
}
