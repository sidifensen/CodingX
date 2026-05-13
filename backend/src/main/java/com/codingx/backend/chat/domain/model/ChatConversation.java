package com.codingx.backend.chat.domain.model;

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
public class ChatConversation {

    private Long id;
    private String title;
    private Long createdBy;
    private ChatConversationStatus status;
    private LocalDateTime lastMessageAt;

    public static ChatConversation create(Long id, String title, Long createdBy, ChatConversationStatus status) {
        if (id == null || createdBy == null || status == null || StrUtil.isBlank(title)) {
            throw new IllegalArgumentException("Conversation fields are required");
        }
        return ChatConversation.builder()
            .id(id)
            .title(title)
            .createdBy(createdBy)
            .status(status)
            .build();
    }

    public void touch() {
        this.lastMessageAt = LocalDateTime.now();
    }
}