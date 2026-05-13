package com.codingx.backend.chat.domain.model;

/**
 * Defines the allowed values used by ChatMessageStatus.
 */
public enum ChatMessageStatus {
    // Enumerated value.
    PENDING,
    // Completed state.
    COMPLETED,
    // Completed with failure.
    FAILED
}
