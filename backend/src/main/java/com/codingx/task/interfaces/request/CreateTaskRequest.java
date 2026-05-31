package com.codingx.task.interfaces.request;

import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.task.domain.model.RuntimeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 创建任务的 HTTP 请求体。
 *
 * @param title 用户输入的任务标题，不能为空。
 * @param description 用户输入的任务说明，可为空。
 * @param runtimeType 任务执行运行时，决定走本地、云端或模拟执行器。
 * @param workspaceId 任务关联工作空间标识，可为空；非空时服务层校验工作空间存在。
 * @param skillCodes 前端选择的技能编码列表，未传时领域对象按空列表处理。
 */
public record CreateTaskRequest(
    @NotBlank(message = ErrorMessageCatalog.TASK_TITLE_REQUIRED) String title, // 用户输入的任务标题，不能为空。
    String description, // 用户输入的任务说明，可为空。
    @NotNull(message = ErrorMessageCatalog.TASK_RUNTIME_TYPE_REQUIRED) RuntimeType runtimeType, // 任务执行运行时，决定走本地、云端或模拟执行器。
    Long workspaceId, // 任务关联工作空间标识，可为空；非空时服务层校验工作空间存在。
    List<String> skillCodes // 前端选择的技能编码列表，未传时领域对象按空列表处理。
) {
}
