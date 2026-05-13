package com.codingx.backend.task.domain.repository;
import com.codingx.backend.task.domain.model.Task;
import java.util.List;
import java.util.Optional;

/**
 * Defines the repository contract exposed by TaskRepository.
 */
public interface TaskRepository {

    /**
     * Persists the state handled by save.
     * @param task input argument.
     */
    void save(Task task);

    /**
     * Finds the data required by findById.
     * @param taskId input argument.
     * @return processing result.
     */
    Optional<Task> findById(Long taskId);

    /**
     * Resolves the required data for requireById or throws when it is missing.
     * @param taskId input argument.
     * @return processing result.
     */
    Task requireById(Long taskId);

    /**
     * Finds the data required by findByCreatedBy.
     * @param createdBy input argument.
     * @return processing result.
     */
    List<Task> findByCreatedBy(Long createdBy);
}
