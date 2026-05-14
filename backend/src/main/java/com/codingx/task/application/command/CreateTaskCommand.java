package com.codingx.task.application.command;
import com.codingx.task.domain.model.RuntimeType;

/**
 * 定义 CreateTaskCommand 使用的数据载体。
 */
public record CreateTaskCommand(
    String title, // 展示标题。
    String description, // 详细描述。
    RuntimeType runtimeType, // 运行时类型。
    Long workspaceId // 关联工作区标识。
) {
}
