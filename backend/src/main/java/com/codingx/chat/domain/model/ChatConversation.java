package com.codingx.chat.domain.model;
import cn.hutool.core.util.StrUtil;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 定义 ChatConversation 的核心领域状态与行为。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatConversation {

    /**
     * 主键标识。
     */
    private Long id;

    /**
     * 展示标题。
     */
    private String title;

    /**
     * 创建人用户标识。
     */
    private Long createdBy;

    /**
     * 当前状态值。
     */
    private ChatConversationStatus status;

    /**
     * 最后消息时间。
     */
    private LocalDateTime lastMessageAt;

    /**
     * 创建 create 所需数据并返回结果。
     * @param id 输入参数。
     * @param title 输入参数。
     * @param createdBy 输入参数。
     * @param status 输入参数。
     * @return 输入参数。
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
     * 刷新 touch 处理的时间或状态。
     */
    public void touch() {
        this.lastMessageAt = LocalDateTime.now();
    }
}
