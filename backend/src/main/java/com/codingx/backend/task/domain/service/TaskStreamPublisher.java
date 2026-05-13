package com.codingx.backend.task.domain.service;

public interface TaskStreamPublisher {

    void publishStatus(Long taskId, String status, String title, String content);

    void publishLog(Long taskId, String title, String content);

    void publishSummary(Long taskId, String summary);

    void publishError(Long taskId, String message);

    void publishCompleted(Long taskId, String status);
}