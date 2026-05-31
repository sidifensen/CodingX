package com.codingx.task.infrastructure.stream;
import com.codingx.task.domain.service.TaskStreamPublisher;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 基于 SSE 的任务事件发布器，负责把运行时事件转发给浏览器连接。
 */
@Component
@RequiredArgsConstructor
public class SseTaskStreamPublisher implements TaskStreamPublisher {

    /**
     * 任务 SSE 注册表，用于按任务标识查找并推送当前浏览器连接。
     */
    private final TaskSseRegistry taskSseRegistry;

    /**
     * 发布任务状态变化事件。
     * @param taskId 任务标识。
     * @param status 最新任务状态。
     * @param title 状态标题。
     * @param content 状态说明内容，可为空。
     */
    @Override
    public void publishStatus(Long taskId, String status, String title, String content) {
        // 步骤 1：组装前端统一消费的状态事件载荷，空 content 按空字符串发送。
        // 步骤 2：交给注册表按 taskId 广播给当前订阅连接。
        taskSseRegistry.publish(taskId, "task-status-changed", Map.of("taskId", taskId, "status", status, "title", title, "content", content == null ? "" : content));
    }

    /**
     * 发布任务执行日志事件。
     * @param taskId 任务标识。
     * @param title 日志标题。
     * @param content 日志正文。
     */
    @Override
    public void publishLog(Long taskId, String title, String content) {
        // 步骤 1：日志事件保留标题和正文，便于前端按时间线展示执行过程。
        // 步骤 2：按任务标识广播给所有当前订阅者。
        taskSseRegistry.publish(taskId, "task-log", Map.of("taskId", taskId, "title", title, "content", content));
    }

    /**
     * 发布任务摘要事件。
     * @param taskId 任务标识。
     * @param summary 最新摘要。
     */
    @Override
    public void publishSummary(Long taskId, String summary) {
        // 步骤 1：摘要事件只携带最新摘要内容，前端据此刷新任务结果区。
        // 步骤 2：发布器不持久化摘要，最终状态由任务仓储负责保存。
        taskSseRegistry.publish(taskId, "task-summary", Map.of("taskId", taskId, "summary", summary));
    }

    /**
     * 发布任务错误事件。
     * @param taskId 任务标识。
     * @param message 错误文案。
     */
    @Override
    public void publishError(Long taskId, String message) {
        // 步骤 1：错误事件携带返回给前端的中文错误文案。
        // 步骤 2：任务是否进入失败终态由运行时和领域对象处理，发布器只负责推送。
        taskSseRegistry.publish(taskId, "task-error", Map.of("taskId", taskId, "message", message));
    }

    /**
     * 发布任务完成事件。
     * @param taskId 任务标识。
     * @param status 最终任务状态。
     */
    @Override
    public void publishCompleted(Long taskId, String status) {
        // 步骤 1：完成事件携带最终状态，通知前端关闭加载态或停止重连。
        // 步骤 2：同一任务多连接订阅时由注册表负责广播。
        taskSseRegistry.publish(taskId, "task-completed", Map.of("taskId", taskId, "status", status));
    }
}
