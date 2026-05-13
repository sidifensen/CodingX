package com.codingx.backend.task.domain.model;

/**
 * Defines the allowed values used by TaskStatus.
 */
public enum TaskStatus {
    // Created but not started yet.
    CREATED,
    // Currently running.
    RUNNING,
    // Completed successfully.
    SUCCEEDED,
    // Completed with failure.
    FAILED
}
