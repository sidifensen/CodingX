package com.codingx.backend.chat.domain.model;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMessage {

    private Long id;
    private Long conversationId;
    private ChatMessageRole role;
    private String content;
    private ChatMessageStatus status;
    private String provider;
    private String model;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ChatMessage userMessage(Long conversationId, String content) {
        return create(IdUtil.getSnowflakeNextId(), conversationId, ChatMessageRole.USER, content, ChatMessageStatus.COMPLETED, null, null, null);
    }

    public static ChatMessage assistantMessage(Long conversationId, String content, ChatMessageStatus status, String provider, String model, String errorMessage) {
        return create(IdUtil.getSnowflakeNextId(), conversationId, ChatMessageRole.ASSISTANT, content, status, provider, model, errorMessage);
    }

    public static ChatMessage create(Long id, Long conversationId, ChatMessageRole role, String content, ChatMessageStatus status, String provider, String model, String errorMessage) {
        if (id == null || conversationId == null || role == null || status == null || StrUtil.isBlank(content)) {
            throw new IllegalArgumentException("Message fields are required");
        }
        LocalDateTime now = LocalDateTime.now();
        return ChatMessage.builder()
            .id(id)
            .conversationId(conversationId)
            .role(role)
            .content(content)
            .status(status)
            .provider(provider)
            .model(model)
            .errorMessage(errorMessage)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}