package com.codingx.backend.chat.interfaces.response;

import com.codingx.backend.chat.domain.model.ChatMessageRole;
import com.codingx.backend.chat.domain.model.ChatMessageStatus;
import java.time.LocalDateTime;

public record ChatMessageResponse(
    Long id,
    Long conversationId,
    ChatMessageRole role,
    String content,
    ChatMessageStatus status,
    String provider,
    String model,
    String errorMessage,
    LocalDateTime createdAt
) {
}