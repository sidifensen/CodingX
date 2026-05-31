package com.codingx.task.application.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.common.exception.NotFoundException;
import com.codingx.common.error.ErrorMessageCatalog;
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
     * 任务仓储，用于验证命令服务保存和读取任务聚合。
     */
    @Mock
    private TaskRepository taskRepository;

    /**
     * 工作空间仓储，用于验证创建任务时的空间存在性校验。
     */
    @Mock
    private WorkspaceRepository workspaceRepository;

    /**
     * 任务运行时执行器，用于验证启动任务后会触发执行。
     */
    @Mock
    private TaskRuntimeExecutor taskRuntimeExecutor;

    /**
     * 被测任务命令应用服务。
     */
    @InjectMocks
    private TaskCommandApplicationService taskCommandApplicationService;

    /**
     * 创建任务应持久化 CREATED 状态任务并保留技能编码。
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
     * 启动任务应加载任务、保存 RUNNING 状态并委托运行时执行。
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
     * 非创建人启动任务时应抛出禁止操作异常。
     */
    @Test
    void startTaskRejectsNonOwnerOperator() {

        Task task = Task.create(1L, "Build backend", "phase 2", RuntimeType.MOCK, null, 1002L);
        when(taskRepository.requireById(1L)).thenReturn(task);
        ForbiddenException exception = assertThrows(
            ForbiddenException.class,
            () -> taskCommandApplicationService.startTask(new StartTaskCommand(1L, 2001L))

        );
        assertEquals(ErrorMessageCatalog.TASK_FORBIDDEN_OPERATE, exception.getMessage());
    }

    /**
     * 创建 createTaskValidatesWorkspaceExists 所需数据并返回结果。
     */
    @Test
    void createTaskValidatesWorkspaceExists() {

        CreateTaskCommand command = new CreateTaskCommand("Build backend", "phase 2", RuntimeType.MOCK, 3001L, java.util.List.of("conversation-core"));
        doThrow(new NotFoundException(ErrorMessageCatalog.WORKSPACE_NOT_FOUND)).when(workspaceRepository).ensureExists(3001L);
        NotFoundException exception = assertThrows(
            NotFoundException.class,
            () -> taskCommandApplicationService.createTask(command, 1002L)

        );
        assertEquals(ErrorMessageCatalog.WORKSPACE_NOT_FOUND, exception.getMessage());
    }
}
