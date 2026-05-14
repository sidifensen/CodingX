package com.codingx.task.interfaces.request;
import com.codingx.task.domain.model.RuntimeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 定义 CreateTaskRequest 使用的数据载体。
 */
public record CreateTaskRequest(
    @NotBlank(message = "title is required") String title, // 展示标题。
    String description, // 详细描述。
    @NotNull(message = "runtimeType is required") RuntimeType runtimeType, // 运行时类型。
    Long workspaceId // 关联工作区标识。
) {
}
