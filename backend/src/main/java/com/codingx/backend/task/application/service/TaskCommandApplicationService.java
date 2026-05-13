package com.codingx.backend.task.application.service;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import com.codingx.backend.common.exception.ForbiddenException;
import com.codingx.backend.runtime.domain.service.TaskRuntimeExecutor;
import com.codingx.backend.task.application.command.CreateTaskCommand;
import com.codingx.backend.task.application.command.StartTaskCommand;
import com.codingx.backend.task.domain.model.Task;
import com.codingx.backend.task.domain.repository.TaskRepository;
import com.codingx.backend.workspace.domain.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Orchestrates application flow for TaskCommandApplicationService by coordinating domain objects and infrastructure services.
 */
@Service
@RequiredArgsConstructor
public class TaskCommandApplicationService {

    /**
     * TaskRepository dependency.
     */
    private final TaskRepository taskRepository;

    /**
     * WorkspaceRepository dependency.
     */
    private final WorkspaceRepository workspaceRepository;

    /**
     * TaskRuntimeExecutor dependency.
     */
    private final TaskRuntimeExecutor taskRuntimeExecutor;

    /**
     * Creates the data required by createTask and returns the result.
     * @param command input argument.
     * @param createdBy input argument.
     * @return processing result.
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
            createdBy
        );
        taskRepository.save(task);
        return task;
    }

    /**
     * Starts the workflow handled by startTask.
     * @param command input argument.
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
