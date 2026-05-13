package com.codingx.backend.event.domain.repository;
import com.codingx.backend.event.domain.model.TaskEvent;
import java.util.List;

/**
 * Defines the repository contract exposed by TaskEventRepository.
 */
public interface TaskEventRepository {

    /**
     * Persists the state handled by save.
     * @param taskEvent input argument.
     */
    void save(TaskEvent taskEvent);

    /**
     * Finds the data required by findByTaskId.
     * @param taskId input argument.
     * @return processing result.
     */
    List<TaskEvent> findByTaskId(Long taskId);

    /**
     * Executes the logic defined by nextSequence.
     * @param taskId input argument.
     * @return processing result.
     */
    long nextSequence(Long taskId);
}
