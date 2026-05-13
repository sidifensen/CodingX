package com.codingx.backend.chat.interfaces.response;
import com.codingx.backend.chat.domain.model.ChatMessageRole;
import com.codingx.backend.chat.domain.model.ChatMessageStatus;
import java.time.LocalDateTime;

/**
 * Represents the request or response data carried by ChatMessageResponse.
 */
public record ChatMessageResponse(
    Long id, // Primary identifier.
    Long conversationId, // Related conversation identifier.
    ChatMessageRole role, // role value.
    String content, // Primary payload content.
    ChatMessageStatus status, // Current status value.
    String provider, // Provider identifier.
    String model, // Model identifier.
    String errorMessage, // Error message.
    LocalDateTime createdAt // Creation timestamp.
) {
}
