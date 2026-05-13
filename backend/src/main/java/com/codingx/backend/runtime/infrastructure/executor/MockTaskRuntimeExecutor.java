package com.codingx.backend.runtime.infrastructure.executor;

import cn.hutool.core.thread.ThreadUtil;
import com.codingx.backend.artifact.domain.model.TaskArtifact;
import com.codingx.backend.artifact.domain.repository.TaskArtifactRepository;
import com.codingx.backend.config.RuntimeProperties;
import com.codingx.backend.event.domain.model.TaskEvent;
import com.codingx.backend.event.domain.repository.TaskEventRepository;
import com.codingx.backend.runtime.domain.service.TaskRuntimeExecutor;
import com.codingx.backend.task.domain.model.Task;
import com.codingx.backend.task.domain.repository.TaskRepository;
import com.codingx.backend.task.domain.service.TaskStreamPublisher;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
public class MockTaskRuntimeExecutor implements TaskRuntimeExecutor {

    private final TaskRepository taskRepository;
    private final TaskEventRepository taskEventRepository;
    private final TaskArtifactRepository taskArtifactRepository;
    private final TaskStreamPublisher taskStreamPublisher;
    private final RuntimeProperties runtimeProperties;

    @Override
    public void execute(Task task) {
        CompletableFuture.runAsync(() -> runTask(task));
    }

    private void runTask(Task task) {
        try {
            appendEvent(task, "task-status", "Task started", "Mock runtime started task execution");
            taskStreamPublisher.publishStatus(task.getId(), task.getStatus().name(), "Task started", "Mock runtime started task execution");
            pause();

            appendEvent(task, "task-log", "Planning", "Analyzing task input and preparing execution steps");
            taskStreamPublisher.publishLog(task.getId(), "Planning", "Analyzing task input and preparing execution steps");
            pause();

            appendEvent(task, "task-log", "Executing", "Running mock backend workflow");
            taskStreamPublisher.publishLog(task.getId(), "Executing", "Running mock backend workflow");
            pause();

            if (task.getTitle().toLowerCase().contains(runtimeProperties.getMockFailKeyword().toLowerCase())) {
                task.fail("Mock runtime detected fail keyword in task title");
                taskRepository.save(task);
                appendEvent(task, "task-error", "Task failed", task.getErrorMessage());
                taskStreamPublisher.publishError(task.getId(), task.getErrorMessage());
                taskStreamPublisher.publishCompleted(task.getId(), task.getStatus().name());
                return;
            }

            String summary = "Mock runtime completed task successfully.";
            task.complete(summary);
            taskRepository.save(task);
            appendEvent(task, "task-summary", "Task summary", summary);
            taskArtifactRepository.save(TaskArtifact.create(task.getId(), "summary", "summary.txt", summary, null));
            taskStreamPublisher.publishSummary(task.getId(), summary);
            taskStreamPublisher.publishCompleted(task.getId(), task.getStatus().name());
        } catch (Exception exception) {
            task.fail(exception.getMessage());
            taskRepository.save(task);
            appendEvent(task, "task-error", "Task failed", exception.getMessage());
            taskStreamPublisher.publishError(task.getId(), exception.getMessage());
            taskStreamPublisher.publishCompleted(task.getId(), task.getStatus().name());
        }
    }

    private void appendEvent(Task task, String eventType, String title, String content) {
        taskEventRepository.save(TaskEvent.create(task.getId(), eventType, taskEventRepository.nextSequence(task.getId()), title, content, null));
    }

    private void pause() {
        ThreadUtil.safeSleep(runtimeProperties.getMockStepDelayMs());
    }
}