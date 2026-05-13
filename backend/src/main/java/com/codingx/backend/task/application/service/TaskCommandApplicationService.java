package com.codingx.backend.task.application.service;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import com.codingx.backend.runtime.domain.service.TaskRuntimeExecutor;
import com.codingx.backend.task.application.command.CreateTaskCommand;
import com.codingx.backend.task.application.command.StartTaskCommand;
import com.codingx.backend.task.domain.model.Task;
import com.codingx.backend.task.domain.repository.TaskRepository;
import com.codingx.backend.workspace.domain.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaskCommandApplicationService {

    private final TaskRepository taskRepository;
    private final WorkspaceRepository workspaceRepository;
    private final TaskRuntimeExecutor taskRuntimeExecutor;

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

    public void startTask(StartTaskCommand command) {
        Task task = taskRepository.requireById(command.taskId());
        task.start();
        taskRepository.save(task);
        taskRuntimeExecutor.execute(task);
    }
}