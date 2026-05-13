package com.codingx.backend.task.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.backend.task.application.command.CreateTaskCommand;
import com.codingx.backend.task.application.command.StartTaskCommand;
import com.codingx.backend.task.domain.model.RuntimeType;
import com.codingx.backend.task.domain.model.Task;
import com.codingx.backend.task.domain.repository.TaskRepository;
import com.codingx.backend.workspace.domain.repository.WorkspaceRepository;
import com.codingx.backend.runtime.domain.service.TaskRuntimeExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskCommandApplicationServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private TaskRuntimeExecutor taskRuntimeExecutor;

    @InjectMocks
    private TaskCommandApplicationService taskCommandApplicationService;

    @Test
    void createTaskPersistsCreatedTask() {
        CreateTaskCommand command = new CreateTaskCommand("Build backend", "phase 2", RuntimeType.MOCK, null);

        Task created = taskCommandApplicationService.createTask(command, 1002L);

        assertEquals("Build backend", created.getTitle());
        assertEquals(RuntimeType.MOCK, created.getRuntimeType());
        verify(taskRepository).save(created);
    }

    @Test
    void startTaskLoadsAndDelegatesToRuntime() {
        Task task = Task.create(1L, "Build backend", "phase 2", RuntimeType.MOCK, null, 1002L);
        when(taskRepository.requireById(1L)).thenReturn(task);

        taskCommandApplicationService.startTask(new StartTaskCommand(1L, 1002L));

        verify(taskRepository).save(task);
        verify(taskRuntimeExecutor).execute(task);
    }
}
