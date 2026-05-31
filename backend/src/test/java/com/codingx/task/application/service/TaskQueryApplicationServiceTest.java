package com.codingx.task.application.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.task.domain.model.RuntimeType;
import com.codingx.task.domain.model.Task;
import com.codingx.task.domain.repository.TaskRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证 TaskQueryApplicationService 的关键场景。
 */
@ExtendWith(MockitoExtension.class)
class TaskQueryApplicationServiceTest {

    /**
     * 任务仓储，用于验证查询服务只读取当前用户可见任务。
     */
    @Mock
    private TaskRepository taskRepository;

    /**
     * 被测任务查询应用服务。
     */
    @InjectMocks
    private TaskQueryApplicationService taskQueryApplicationService;

    /**
     * 查询本人任务详情时应返回任务聚合。
     */
    @Test
    void getTaskReturnsOwnedTask() {

        Task task = Task.create(1L, "Build backend", "phase 2", RuntimeType.MOCK, null, 1002L);
        when(taskRepository.requireById(1L)).thenReturn(task);
        Task result = taskQueryApplicationService.getTask(1L, 1002L);
        assertEquals(1L, result.getId());
    }

    /**
     * 查询他人任务详情时应抛出禁止访问异常。
     */
    @Test
    void getTaskRejectsNonOwner() {

        Task task = Task.create(1L, "Build backend", "phase 2", RuntimeType.MOCK, null, 1002L);
        when(taskRepository.requireById(1L)).thenReturn(task);
        ForbiddenException exception = assertThrows(
            ForbiddenException.class,
            () -> taskQueryApplicationService.getTask(1L, 2001L)

        );
        assertEquals(ErrorMessageCatalog.TASK_FORBIDDEN_ACCESS, exception.getMessage());
    }

    /**
     * 列表查询应只使用当前用户作为创建人条件。
     */
    @Test
    void listTasksOnlyUsesCurrentOwner() {

        List<Task> tasks = List.of(Task.create(1L, "Build backend", "phase 2", RuntimeType.MOCK, null, 1002L));
        when(taskRepository.findByCreatedBy(1002L)).thenReturn(tasks);
        List<Task> result = taskQueryApplicationService.listTasks(1002L);
        assertEquals(1, result.size());
        assertEquals(1002L, result.getFirst().getCreatedBy());
    }
}
