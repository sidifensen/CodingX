package com.codingx.backend.task.application.service;
import com.codingx.backend.common.exception.ForbiddenException;
import com.codingx.backend.task.domain.model.Task;
import com.codingx.backend.task.domain.repository.TaskRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Orchestrates application flow for TaskQueryApplicationService by coordinating domain objects and infrastructure services.
 */
@Service
@RequiredArgsConstructor
public class TaskQueryApplicationService {

    /**
     * TaskRepository dependency.
     */
    private final TaskRepository taskRepository;

    /**
     * Returns the collection required by listTasks.
     * @param createdBy input argument.
     * @return processing result.
     */
    public List<Task> listTasks(Long createdBy) {
        return taskRepository.findByCreatedBy(createdBy);
    }

    /**
     * Retrieves the result required by getTask.
     * @param taskId input argument.
     * @param currentUserId input argument.
     * @return processing result.
     */
    public Task getTask(Long taskId, Long currentUserId) {
        Task task = taskRepository.requireById(taskId);
        if (!task.getCreatedBy().equals(currentUserId)) {
            throw new ForbiddenException("You cannot access this task");
        }
        return task;
    }
}
