package com.codingx.chat.interfaces.response;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import java.time.LocalDateTime;

/**
 * 定义 ChatMessageResponse 使用的数据载体。
 */
public record ChatMessageResponse(
    Long id, // 主键标识。
    Long conversationId, // 关联会话标识。
    ChatMessageRole role, // role 字段。
    String content, // 主体内容。
    ChatMessageStatus status, // 当前状态值。
    String provider, // 提供方标识。
    String model, // 模型标识。
    String errorMessage, // 错误信息。
    LocalDateTime createdAt // 创建时间。
) {
}
