package com.codingx.chat.domain.model;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 定义 ChatMessage 的核心领域状态与行为。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMessage {

    /**
     * 主键标识。
     */
    private Long id;

    /**
     * 关联会话标识。
     */
    private Long conversationId;

    /**
     * role 字段。
     */
    private ChatMessageRole role;

    /**
     * 主体内容。
     */
    private String content;

    /**
     * 当前状态值。
     */
    private ChatMessageStatus status;

    /**
     * 提供方标识。
     */
    private String provider;

    /**
     * 模型标识。
     */
    private String model;

    /**
     * 错误信息。
     */
    private String errorMessage;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 最后更新时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 执行 userMessage 定义的处理逻辑。
     * @param conversationId 输入参数。
     * @param content 输入参数。
     * @return 输入参数。
     */
    public static ChatMessage userMessage(Long conversationId, String content) {
        return create(IdUtil.getSnowflakeNextId(), conversationId, ChatMessageRole.USER, content, ChatMessageStatus.COMPLETED, null, null, null);
    }

    /**
     * 执行 assistantMessage 定义的处理逻辑。
     * @param conversationId 输入参数。
     * @param content 输入参数。
     * @param status 输入参数。
     * @param provider 输入参数。
     * @param model 输入参数。
     * @param errorMessage 输入参数。
     * @return 输入参数。
     */
    public static ChatMessage assistantMessage(Long conversationId, String content, ChatMessageStatus status, String provider, String model, String errorMessage) {
        return create(IdUtil.getSnowflakeNextId(), conversationId, ChatMessageRole.ASSISTANT, content, status, provider, model, errorMessage);
    }

    /**
     * 创建 create 所需数据并返回结果。
     * @param id 输入参数。
     * @param conversationId 输入参数。
     * @param role 输入参数。
     * @param content 输入参数。
     * @param status 输入参数。
     * @param provider 输入参数。
     * @param model 输入参数。
     * @param errorMessage 输入参数。
     * @return 输入参数。
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
