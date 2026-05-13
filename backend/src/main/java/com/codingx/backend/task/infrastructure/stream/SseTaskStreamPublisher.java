package com.codingx.backend.task.infrastructure.stream;
import com.codingx.backend.task.domain.service.TaskStreamPublisher;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Publishes streaming updates for SseTaskStreamPublisher.
 */
@Component
@RequiredArgsConstructor
public class SseTaskStreamPublisher implements TaskStreamPublisher {

    /**
     * taskSseRegistry value.
     */
    private final TaskSseRegistry taskSseRegistry;

    /**
     * Publishes the update handled by publishStatus.
     * @param taskId input argument.
     * @param status input argument.
     * @param title input argument.
     * @param content input argument.
     */
    @Override
    public void publishStatus(Long taskId, String status, String title, String content) {
        taskSseRegistry.publish(taskId, "task-status-changed", Map.of("taskId", taskId, "status", status, "title", title, "content", content == null ? "" : content));
    }

    /**
     * Publishes the update handled by publishLog.
     * @param taskId input argument.
     * @param title input argument.
     * @param content input argument.
     */
    @Override
    public void publishLog(Long taskId, String title, String content) {
        taskSseRegistry.publish(taskId, "task-log", Map.of("taskId", taskId, "title", title, "content", content));
    }

    /**
     * Publishes the update handled by publishSummary.
     * @param taskId input argument.
     * @param summary input argument.
     */
    @Override
    public void publishSummary(Long taskId, String summary) {
        taskSseRegistry.publish(taskId, "task-summary", Map.of("taskId", taskId, "summary", summary));
    }

    /**
     * Publishes the update handled by publishError.
     * @param taskId input argument.
     * @param message input argument.
     */
    @Override
    public void publishError(Long taskId, String message) {
        taskSseRegistry.publish(taskId, "task-error", Map.of("taskId", taskId, "message", message));
    }

    /**
     * Publishes the update handled by publishCompleted.
     * @param taskId input argument.
     * @param status input argument.
     */
    @Override
    public void publishCompleted(Long taskId, String status) {
        taskSseRegistry.publish(taskId, "task-completed", Map.of("taskId", taskId, "status", status));
    }
}
