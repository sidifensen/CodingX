package com.codingx.backend.chat.domain.model;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Models the core domain state and behavior for ChatMessage.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMessage {

    /**
     * Primary identifier.
     */
    private Long id;
    /**
     * Related conversation identifier.
     */
    private Long conversationId;
    /**
     * role value.
     */
    private ChatMessageRole role;
    /**
     * Primary payload content.
     */
    private String content;
    /**
     * Current status value.
     */
    private ChatMessageStatus status;
    /**
     * Provider identifier.
     */
    private String provider;
    /**
     * Model identifier.
     */
    private String model;
    /**
     * Error message.
     */
    private String errorMessage;
    /**
     * Creation timestamp.
     */
    private LocalDateTime createdAt;
    /**
     * Last update timestamp.
     */
    private LocalDateTime updatedAt;

    /**
     * Executes the logic defined by userMessage.
     * @param conversationId input argument.
     * @param content input argument.
     * @return processing result.
     */
    public static ChatMessage userMessage(Long conversationId, String content) {
        return create(IdUtil.getSnowflakeNextId(), conversationId, ChatMessageRole.USER, content, ChatMessageStatus.COMPLETED, null, null, null);
    }

    /**
     * Executes the logic defined by assistantMessage.
     * @param conversationId input argument.
     * @param content input argument.
     * @param status input argument.
     * @param provider input argument.
     * @param model input argument.
     * @param errorMessage input argument.
     * @return processing result.
     */
    public static ChatMessage assistantMessage(Long conversationId, String content, ChatMessageStatus status, String provider, String model, String errorMessage) {
        return create(IdUtil.getSnowflakeNextId(), conversationId, ChatMessageRole.ASSISTANT, content, status, provider, model, errorMessage);
    }

    /**
     * Creates the data required by create and returns the result.
     * @param id input argument.
     * @param conversationId input argument.
     * @param role input argument.
     * @param content input argument.
     * @param status input argument.
     * @param provider input argument.
     * @param model input argument.
     * @param errorMessage input argument.
     * @return processing result.
     */
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
