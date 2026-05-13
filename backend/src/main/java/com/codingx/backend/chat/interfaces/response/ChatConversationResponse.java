package com.codingx.backend.chat.interfaces.response;

import com.codingx.backend.chat.domain.model.ChatConversationStatus;
import java.time.LocalDateTime;

public record ChatConversationResponse(Long id, String title, ChatConversationStatus status, LocalDateTime lastMessageAt) {
}