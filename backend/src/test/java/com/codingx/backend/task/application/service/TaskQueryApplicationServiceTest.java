package com.codingx.backend.task.application.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import com.codingx.backend.common.exception.ForbiddenException;
import com.codingx.backend.task.domain.model.RuntimeType;
import com.codingx.backend.task.domain.model.Task;
import com.codingx.backend.task.domain.repository.TaskRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests the key scenarios covered by TaskQueryApplicationService.
 */
@ExtendWith(MockitoExtension.class)
class TaskQueryApplicationServiceTest {

    /**
     * TaskRepository dependency.
     */
    @Mock
    private TaskRepository taskRepository;

    /**
     * TaskQueryApplicationService dependency.
     */
    @InjectMocks
    private TaskQueryApplicationService taskQueryApplicationService;

    /**
     * Retrieves the result required by getTaskReturnsOwnedTask.
     */
    @Test
    void getTaskReturnsOwnedTask() {

        Task task = Task.create(1L, "Build backend", "phase 2", RuntimeType.MOCK, null, 1002L);
        when(taskRepository.requireById(1L)).thenReturn(task);
        Task result = taskQueryApplicationService.getTask(1L, 1002L);
        assertEquals(1L, result.getId());
    }

    /**
     * Retrieves the result required by getTaskRejectsNonOwner.
     */
    @Test
    void getTaskRejectsNonOwner() {

        Task task = Task.create(1L, "Build backend", "phase 2", RuntimeType.MOCK, null, 1002L);
        when(taskRepository.requireById(1L)).thenReturn(task);
        ForbiddenException exception = assertThrows(
            ForbiddenException.class,
            () -> taskQueryApplicationService.getTask(1L, 2001L)

        );
        assertEquals("You cannot access this task", exception.getMessage());
    }

    /**
     * Returns the collection required by listTasksOnlyUsesCurrentOwner.
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
