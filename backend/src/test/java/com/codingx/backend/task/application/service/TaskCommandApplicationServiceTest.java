package com.codingx.backend.task.application.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.codingx.backend.common.exception.ForbiddenException;
import com.codingx.backend.common.exception.NotFoundException;
import com.codingx.backend.runtime.domain.service.TaskRuntimeExecutor;
import com.codingx.backend.task.application.command.CreateTaskCommand;
import com.codingx.backend.task.application.command.StartTaskCommand;
import com.codingx.backend.task.domain.model.RuntimeType;
import com.codingx.backend.task.domain.model.Task;
import com.codingx.backend.task.domain.repository.TaskRepository;
import com.codingx.backend.workspace.domain.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests the key scenarios covered by TaskCommandApplicationService.
 */
@ExtendWith(MockitoExtension.class)
class TaskCommandApplicationServiceTest {

    /**
     * TaskRepository dependency.
     */
    @Mock
    private TaskRepository taskRepository;

    /**
     * WorkspaceRepository dependency.
     */
    @Mock
    private WorkspaceRepository workspaceRepository;

    /**
     * TaskRuntimeExecutor dependency.
     */
    @Mock
    private TaskRuntimeExecutor taskRuntimeExecutor;

    /**
     * TaskCommandApplicationService dependency.
     */
    @InjectMocks
    private TaskCommandApplicationService taskCommandApplicationService;

    /**
     * Creates the data required by createTaskPersistsCreatedTask and returns the result.
     */
    @Test
    void createTaskPersistsCreatedTask() {

        CreateTaskCommand command = new CreateTaskCommand("Build backend", "phase 2", RuntimeType.MOCK, null);
        Task created = taskCommandApplicationService.createTask(command, 1002L);
        assertEquals("Build backend", created.getTitle());
        assertEquals(RuntimeType.MOCK, created.getRuntimeType());
        verify(taskRepository).save(created);
    }

    /**
     * Starts the workflow handled by startTaskLoadsAndDelegatesToRuntime.
     */
    @Test
    void startTaskLoadsAndDelegatesToRuntime() {

        Task task = Task.create(1L, "Build backend", "phase 2", RuntimeType.MOCK, null, 1002L);
        when(taskRepository.requireById(1L)).thenReturn(task);
        taskCommandApplicationService.startTask(new StartTaskCommand(1L, 1002L));
        verify(taskRepository).save(task);
        verify(taskRuntimeExecutor).execute(task);
    }

    /**
     * Starts the workflow handled by startTaskRejectsNonOwnerOperator.
     */
    @Test
    void startTaskRejectsNonOwnerOperator() {

        Task task = Task.create(1L, "Build backend", "phase 2", RuntimeType.MOCK, null, 1002L);
        when(taskRepository.requireById(1L)).thenReturn(task);
        ForbiddenException exception = assertThrows(
            ForbiddenException.class,
            () -> taskCommandApplicationService.startTask(new StartTaskCommand(1L, 2001L))

        );
        assertEquals("You cannot operate this task", exception.getMessage());
    }

    /**
     * Creates the data required by createTaskValidatesWorkspaceExists and returns the result.
     */
    @Test
    void createTaskValidatesWorkspaceExists() {

        CreateTaskCommand command = new CreateTaskCommand("Build backend", "phase 2", RuntimeType.MOCK, 3001L);
        doThrow(new NotFoundException("Workspace not found")).when(workspaceRepository).ensureExists(3001L);
        NotFoundException exception = assertThrows(
            NotFoundException.class,
            () -> taskCommandApplicationService.createTask(command, 1002L)

        );
        assertEquals("Workspace not found", exception.getMessage());
    }
}
