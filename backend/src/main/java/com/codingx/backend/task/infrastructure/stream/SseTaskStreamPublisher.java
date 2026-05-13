package com.codingx.backend.task.infrastructure.stream;

import com.codingx.backend.task.domain.service.TaskStreamPublisher;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SseTaskStreamPublisher implements TaskStreamPublisher {

    private final TaskSseRegistry taskSseRegistry;

    @Override
    public void publishStatus(Long taskId, String status, String title, String content) {
        taskSseRegistry.publish(taskId, "task-status-changed", Map.of("taskId", taskId, "status", status, "title", title, "content", content == null ? "" : content));
    }

    @Override
    public void publishLog(Long taskId, String title, String content) {
        taskSseRegistry.publish(taskId, "task-log", Map.of("taskId", taskId, "title", title, "content", content));
    }

    @Override
    public void publishSummary(Long taskId, String summary) {
        taskSseRegistry.publish(taskId, "task-summary", Map.of("taskId", taskId, "summary", summary));
    }

    @Override
    public void publishError(Long taskId, String message) {
        taskSseRegistry.publish(taskId, "task-error", Map.of("taskId", taskId, "message", message));
    }

    @Override
    public void publishCompleted(Long taskId, String status) {
        taskSseRegistry.publish(taskId, "task-completed", Map.of("taskId", taskId, "status", status));
    }
}