package com.codingx.backend.runtime.domain.service;
import com.codingx.backend.task.domain.model.Task;

/**
 * Defines the domain service contract exposed by TaskRuntimeExecutor.
 */
public interface TaskRuntimeExecutor {

    /**
     * Executes the logic defined by execute.
     * @param task input argument.
     */
    void execute(Task task);
}
