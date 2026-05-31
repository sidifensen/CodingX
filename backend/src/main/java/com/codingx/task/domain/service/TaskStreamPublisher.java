package com.codingx.task.domain.service;

/**
 * 任务流事件发布契约，供运行时向前端推送任务过程状态。
 */
public interface TaskStreamPublisher {

    /**
     * 发布任务状态变化事件。
     * @param taskId 任务标识。
     * @param status 最新任务状态。
     * @param title 状态事件标题。
     * @param content 状态说明内容，可为空。
     */
    void publishStatus(Long taskId, String status, String title, String content);

    /**
     * 发布任务执行日志事件。
     * @param taskId 任务标识。
     * @param title 日志标题。
     * @param content 日志正文。
     */
    void publishLog(Long taskId, String title, String content);

    /**
     * 发布任务摘要事件。
     * @param taskId 任务标识。
     * @param summary 最新摘要内容。
     */
    void publishSummary(Long taskId, String summary);

    /**
     * 发布任务错误事件。
     * @param taskId 任务标识。
     * @param message 返回给前端的错误文案。
     */
    void publishError(Long taskId, String message);

    /**
     * 发布任务完成事件。
     * @param taskId 任务标识。
     * @param status 最终任务状态。
     */
    void publishCompleted(Long taskId, String status);
}
