package com.codingx.task.application.service;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.task.domain.model.Task;
import com.codingx.task.domain.repository.TaskRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 任务查询应用服务，负责按当前用户读取任务并做归属校验。
 */
@Service
@RequiredArgsConstructor
public class TaskQueryApplicationService {

    /**
     * 任务仓储，用于按创建人查询任务列表和加载任务详情。
     */
    private final TaskRepository taskRepository;

    /**
     * 查询指定用户创建的任务列表。
     * @param createdBy 当前用户标识。
     * @return 任务列表。
     */
    public List<Task> listTasks(Long createdBy) {
        // 步骤 1：列表查询只使用当前登录用户作为创建人条件，不接受前端传入 owner。
        // 步骤 2：排序和过滤由仓储实现统一处理，应用层不重复拼装查询条件。
        return taskRepository.findByCreatedBy(createdBy);
    }

    /**
     * 查询任务详情并校验归属。
     * @param taskId 任务标识。
     * @param currentUserId 当前用户标识。
     * @return 任务聚合。
     */
    public Task getTask(Long taskId, Long currentUserId) {
        // 步骤 1：先按任务主键加载聚合，任务不存在时由仓储抛出统一未找到异常。
        Task task = taskRepository.requireById(taskId);
        // 步骤 2：任务详情和 SSE 订阅都必须校验创建人，防止越权读取任务执行过程。
        if (!task.getCreatedBy().equals(currentUserId)) {
            throw new ForbiddenException(ErrorMessageCatalog.TASK_FORBIDDEN_ACCESS);
        }
        // 步骤 3：归属校验通过后返回领域对象，响应投影由视图服务处理。
        return task;
    }
}
