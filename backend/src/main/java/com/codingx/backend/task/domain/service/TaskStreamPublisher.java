package com.codingx.backend.task.domain.service;

/**
 * Defines the domain service contract exposed by TaskStreamPublisher.
 */
public interface TaskStreamPublisher {

    /**
     * Publishes the update handled by publishStatus.
     * @param taskId input argument.
     * @param status input argument.
     * @param title input argument.
     * @param content input argument.
     */
    void publishStatus(Long taskId, String status, String title, String content);

    /**
     * Publishes the update handled by publishLog.
     * @param taskId input argument.
     * @param title input argument.
     * @param content input argument.
     */
    void publishLog(Long taskId, String title, String content);

    /**
     * Publishes the update handled by publishSummary.
     * @param taskId input argument.
     * @param summary input argument.
     */
    void publishSummary(Long taskId, String summary);

    /**
     * Publishes the update handled by publishError.
     * @param taskId input argument.
     * @param message input argument.
     */
    void publishError(Long taskId, String message);

    /**
     * Publishes the update handled by publishCompleted.
     * @param taskId input argument.
     * @param status input argument.
     */
    void publishCompleted(Long taskId, String status);
}
