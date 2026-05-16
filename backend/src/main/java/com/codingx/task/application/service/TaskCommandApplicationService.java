package com.codingx.task.application.service;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.runtime.domain.service.TaskRuntimeExecutor;
import com.codingx.task.application.command.CreateTaskCommand;
import com.codingx.task.application.command.StartTaskCommand;
import com.codingx.task.domain.model.Task;
import com.codingx.task.domain.repository.TaskRepository;
import com.codingx.workspace.domain.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责协调 TaskCommandApplicationService 的应用流程，串联领域对象与基础设施服务。
 */
@Service
@RequiredArgsConstructor
public class TaskCommandApplicationService {

    /**
     * TaskRepository 依赖。
     */
    private final TaskRepository taskRepository;

    /**
     * WorkspaceRepository 依赖。
     */
    private final WorkspaceRepository workspaceRepository;

    /**
     * TaskRuntimeExecutor 依赖。
     */
    private final TaskRuntimeExecutor taskRuntimeExecutor;

    /**
     * 创建 createTask 所需数据并返回结果。
     * @param command 输入参数。
     * @param createdBy 输入参数。
     * @return 输入参数。
     */
    public Task createTask(CreateTaskCommand command, Long createdBy) {
        Assert.notNull(command, "Create task command is required");
        if (command.workspaceId() != null) {
            workspaceRepository.ensureExists(command.workspaceId());
        }
        Task task = Task.create(
            IdUtil.getSnowflakeNextId(),
            command.title(),
            command.description(),
            command.runtimeType(),
            command.workspaceId(),
            createdBy,
            command.skillCodes()
        );
        taskRepository.save(task);
        return task;
    }

    /**
     * 启动 startTask 处理的流程。
     * @param command 输入参数。
     */
    public void startTask(StartTaskCommand command) {
        Task task = taskRepository.requireById(command.taskId());
        if (!task.getCreatedBy().equals(command.operatorId())) {
            throw new ForbiddenException("You cannot operate this task");
        }
        task.start();
        taskRepository.save(task);
        taskRuntimeExecutor.execute(task);
    }
}
