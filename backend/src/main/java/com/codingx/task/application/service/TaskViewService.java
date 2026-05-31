package com.codingx.task.application.service;

import com.codingx.task.domain.model.Task;
import com.codingx.task.interfaces.response.TaskResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 负责把任务领域对象投影为接口响应对象。
 */
@Service
public class TaskViewService {

    /**
     * 将任务领域对象转换为前端可展示的响应结构。
     * @param task 任务领域对象。
     * @return 任务响应对象。
     */
    public TaskResponse toResponse(Task task) {
        // 步骤 1：读取任务领域对象中的基础字段，保持 Long 标识和枚举状态原样交给统一序列化层处理。
        // 步骤 2：补齐运行结果字段，未完成任务的 summary 或 errorMessage 按领域对象当前值返回。
        return new TaskResponse(
            task.getId(),
            task.getTitle(),
            task.getDescription(),
            task.getStatus(),
            task.getRuntimeType(),
            task.getWorkspaceId(),
            task.getCreatedBy(),
            task.getSummary(),
            task.getErrorMessage()
        );
    }

    /**
     * 将任务列表按仓储返回顺序批量转换为响应列表。
     * @param tasks 任务领域对象列表。
     * @return 任务响应列表。
     */
    public List<TaskResponse> toResponses(List<Task> tasks) {
        // 步骤 1：列表顺序已经由查询服务和仓储确定，视图服务只做逐项投影。
        // 步骤 2：返回不可变 stream 结果，Controller 不再重复实现字段映射。
        return tasks.stream()
            .map(this::toResponse)
            .toList();
    }
}
