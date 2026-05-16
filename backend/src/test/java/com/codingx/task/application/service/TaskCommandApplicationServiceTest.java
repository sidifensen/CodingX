package com.codingx.task.application.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.common.exception.NotFoundException;
import com.codingx.runtime.domain.service.TaskRuntimeExecutor;
import com.codingx.task.application.command.CreateTaskCommand;
import com.codingx.task.application.command.StartTaskCommand;
import com.codingx.task.domain.model.RuntimeType;
import com.codingx.task.domain.model.Task;
import com.codingx.task.domain.repository.TaskRepository;
import com.codingx.workspace.domain.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证 TaskCommandApplicationService 的关键场景。
 */
@ExtendWith(MockitoExtension.class)
class TaskCommandApplicationServiceTest {

    /**
     * TaskRepository 依赖。
     */
    @Mock
    private TaskRepository taskRepository;

    /**
     * WorkspaceRepository 依赖。
     */
    @Mock
    private WorkspaceRepository workspaceRepository;

    /**
     * TaskRuntimeExecutor 依赖。
     */
    @Mock
    private TaskRuntimeExecutor taskRuntimeExecutor;

    /**
     * TaskCommandApplicationService 依赖。
     */
    @InjectMocks
    private TaskCommandApplicationService taskCommandApplicationService;

    /**
     * 创建 createTaskPersistsCreatedTask 所需数据并返回结果。
     */
    @Test
    void createTaskPersistsCreatedTask() {

        CreateTaskCommand command = new CreateTaskCommand("Build backend", "phase 2", RuntimeType.MOCK, null, java.util.List.of("conversation-core"));
        Task created = taskCommandApplicationService.createTask(command, 1002L);
        assertEquals("Build backend", created.getTitle());
        assertEquals(RuntimeType.MOCK, created.getRuntimeType());
        assertEquals(java.util.List.of("conversation-core"), created.getSkillCodes());
        verify(taskRepository).save(created);
    }

    /**
     * 启动 startTaskLoadsAndDelegatesToRuntime 处理的流程。
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
     * 启动 startTaskRejectsNonOwnerOperator 处理的流程。
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
     * 创建 createTaskValidatesWorkspaceExists 所需数据并返回结果。
     */
    @Test
    void createTaskValidatesWorkspaceExists() {

        CreateTaskCommand command = new CreateTaskCommand("Build backend", "phase 2", RuntimeType.MOCK, 3001L, java.util.List.of("conversation-core"));
        doThrow(new NotFoundException("Workspace not found")).when(workspaceRepository).ensureExists(3001L);
        NotFoundException exception = assertThrows(
            NotFoundException.class,
            () -> taskCommandApplicationService.createTask(command, 1002L)

        );
        assertEquals("Workspace not found", exception.getMessage());
    }
}
