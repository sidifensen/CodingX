package com.codingx.task.interfaces.response;
import com.codingx.task.domain.model.RuntimeType;
import com.codingx.task.domain.model.TaskStatus;

/**
 * 定义 TaskResponse 使用的数据载体。
 */
public record TaskResponse(
    Long id, // 主键标识。
    String title, // 展示标题。
    String description, // 详细描述。
    TaskStatus status, // 当前状态值。
    RuntimeType runtimeType, // 运行时类型。
    Long workspaceId, // 关联工作区标识。
    Long createdBy, // 创建人用户标识。
    String summary, // 摘要内容。
    String errorMessage // 错误信息。
) {
}
