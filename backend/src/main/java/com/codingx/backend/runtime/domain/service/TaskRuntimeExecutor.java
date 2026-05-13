package com.codingx.backend.runtime.domain.service;

import com.codingx.backend.task.domain.model.Task;

public interface TaskRuntimeExecutor {

    void execute(Task task);
}