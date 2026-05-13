package com.codingx.backend.chat.interfaces.response;
import com.codingx.backend.chat.domain.model.ChatConversationStatus;
import java.time.LocalDateTime;

/**
 * Represents the request or response data carried by ChatConversationResponse.
 */
public record ChatConversationResponse(
    Long id, // Primary identifier.
    String title, // Display title.
    ChatConversationStatus status, // Current status value.
    LocalDateTime lastMessageAt // Last message timestamp.
) {
}
