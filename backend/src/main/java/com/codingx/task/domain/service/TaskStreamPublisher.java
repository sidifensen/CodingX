package com.codingx.task.domain.service;

/**
 * 定义 TaskStreamPublisher 的领域服务契约。
 */
public interface TaskStreamPublisher {

    /**
     * 发布 publishStatus 处理的更新内容。
     * @param taskId 输入参数。
     * @param status 输入参数。
     * @param title 输入参数。
     * @param content 输入参数。
     */
    void publishStatus(Long taskId, String status, String title, String content);

    /**
     * 发布 publishLog 处理的更新内容。
     * @param taskId 输入参数。
     * @param title 输入参数。
     * @param content 输入参数。
     */
    void publishLog(Long taskId, String title, String content);

    /**
     * 发布 publishSummary 处理的更新内容。
     * @param taskId 输入参数。
     * @param summary 输入参数。
     */
    void publishSummary(Long taskId, String summary);

    /**
     * 发布 publishError 处理的更新内容。
     * @param taskId 输入参数。
     * @param message 输入参数。
     */
    void publishError(Long taskId, String message);

    /**
     * 发布 publishCompleted 处理的更新内容。
     * @param taskId 输入参数。
     * @param status 输入参数。
     */
    void publishCompleted(Long taskId, String status);
}
