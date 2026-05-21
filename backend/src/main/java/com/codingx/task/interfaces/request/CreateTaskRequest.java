package com.codingx.task.interfaces.request;

import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.task.domain.model.RuntimeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 定义 CreateTaskRequest 使用的数据载体。
 */
public record CreateTaskRequest(
    @NotBlank(message = ErrorMessageCatalog.TASK_TITLE_REQUIRED) String title, // 展示标题。
    String description, // 详细描述。
    @NotNull(message = ErrorMessageCatalog.TASK_RUNTIME_TYPE_REQUIRED) RuntimeType runtimeType, // 运行时类型。
    Long workspaceId, // 关联工作区标识。
    List<String> skillCodes // 绑定技能编码。
) {
}
