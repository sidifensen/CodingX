package com.codingx.backend.task.domain.repository;

import com.codingx.backend.task.domain.model.Task;
import java.util.List;
import java.util.Optional;

public interface TaskRepository {

    void save(Task task);

    Optional<Task> findById(Long taskId);

    Task requireById(Long taskId);

    List<Task> findByCreatedBy(Long createdBy);
}