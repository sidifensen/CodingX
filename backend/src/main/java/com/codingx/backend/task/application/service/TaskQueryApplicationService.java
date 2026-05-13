package com.codingx.backend.task.application.service;

import com.codingx.backend.task.domain.model.Task;
import com.codingx.backend.task.domain.repository.TaskRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaskQueryApplicationService {

    private final TaskRepository taskRepository;

    public List<Task> listTasks(Long createdBy) {
        return taskRepository.findByCreatedBy(createdBy);
    }

    public Task getTask(Long taskId) {
        return taskRepository.requireById(taskId);
    }
}