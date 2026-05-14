package com.codingx.task.application.service;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.task.domain.model.Task;
import com.codingx.task.domain.repository.TaskRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责协调 TaskQueryApplicationService 的应用流程，串联领域对象与基础设施服务。
 */
@Service
@RequiredArgsConstructor
public class TaskQueryApplicationService {

    /**
     * TaskRepository 依赖。
     */
    private final TaskRepository taskRepository;

    /**
     * 返回 listTasks 需要的结果集合。
     * @param createdBy 输入参数。
     * @return 输入参数。
     */
    public List<Task> listTasks(Long createdBy) {
        return taskRepository.findByCreatedBy(createdBy);
    }

    /**
     * 获取 getTask 对应的结果。
     * @param taskId 输入参数。
     * @param currentUserId 输入参数。
     * @return 输入参数。
     */
    public Task getTask(Long taskId, Long currentUserId) {
        Task task = taskRepository.requireById(taskId);
        if (!task.getCreatedBy().equals(currentUserId)) {
            throw new ForbiddenException("You cannot access this task");
        }
        return task;
    }
}
