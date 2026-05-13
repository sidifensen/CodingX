package com.codingx.backend.chat.domain.model;
import cn.hutool.core.util.StrUtil;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Models the core domain state and behavior for ChatConversation.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatConversation {

    /**
     * Primary identifier.
     */
    private Long id;
    /**
     * Display title.
     */
    private String title;
    /**
     * Creator user identifier.
     */
    private Long createdBy;
    /**
     * Current status value.
     */
    private ChatConversationStatus status;
    /**
     * Last message timestamp.
     */
    private LocalDateTime lastMessageAt;

    /**
     * Creates the data required by create and returns the result.
     * @param id input argument.
     * @param title input argument.
     * @param createdBy input argument.
     * @param status input argument.
     * @return processing result.
     */
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

    /**
     * Refreshes the timestamp or state handled by touch.
     */
    public void touch() {
        this.lastMessageAt = LocalDateTime.now();
    }
}
