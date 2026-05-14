package com.codingx.task.infrastructure.stream;
import com.codingx.task.domain.service.TaskStreamPublisher;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 负责 SseTaskStreamPublisher 的流式事件发布。
 */
@Component
@RequiredArgsConstructor
public class SseTaskStreamPublisher implements TaskStreamPublisher {

    /**
     * TaskSseRegistry 依赖。
     */
    private final TaskSseRegistry taskSseRegistry;

    /**
     * 发布 publishStatus 处理的更新内容。
     * @param taskId 输入参数。
     * @param status 输入参数。
     * @param title 输入参数。
     * @param content 输入参数。
     */
    @Override
    public void publishStatus(Long taskId, String status, String title, String content) {
        taskSseRegistry.publish(taskId, "task-status-changed", Map.of("taskId", taskId, "status", status, "title", title, "content", content == null ? "" : content));
    }

    /**
     * 发布 publishLog 处理的更新内容。
     * @param taskId 输入参数。
     * @param title 输入参数。
     * @param content 输入参数。
     */
    @Override
    public void publishLog(Long taskId, String title, String content) {
        taskSseRegistry.publish(taskId, "task-log", Map.of("taskId", taskId, "title", title, "content", content));
    }

    /**
     * 发布 publishSummary 处理的更新内容。
     * @param taskId 输入参数。
     * @param summary 输入参数。
     */
    @Override
    public void publishSummary(Long taskId, String summary) {
        taskSseRegistry.publish(taskId, "task-summary", Map.of("taskId", taskId, "summary", summary));
    }

    /**
     * 发布 publishError 处理的更新内容。
     * @param taskId 输入参数。
     * @param message 输入参数。
     */
    @Override
    public void publishError(Long taskId, String message) {
        taskSseRegistry.publish(taskId, "task-error", Map.of("taskId", taskId, "message", message));
    }

    /**
     * 发布 publishCompleted 处理的更新内容。
     * @param taskId 输入参数。
     * @param status 输入参数。
     */
    @Override
    public void publishCompleted(Long taskId, String status) {
        taskSseRegistry.publish(taskId, "task-completed", Map.of("taskId", taskId, "status", status));
    }
}
