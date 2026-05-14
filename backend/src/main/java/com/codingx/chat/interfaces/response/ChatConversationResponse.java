package com.codingx.chat.interfaces.response;
import com.codingx.chat.domain.model.ChatConversationStatus;
import java.time.LocalDateTime;

/**
 * 定义 ChatConversationResponse 使用的数据载体。
 */
public record ChatConversationResponse(
    Long id, // 主键标识。
    String title, // 展示标题。
    ChatConversationStatus status, // 当前状态值。
    LocalDateTime lastMessageAt // 最后消息时间。
) {
}
