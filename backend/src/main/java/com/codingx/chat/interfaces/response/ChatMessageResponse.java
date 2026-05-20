package com.codingx.chat.interfaces.response;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 定义 ChatMessageResponse 使用的数据载体。
 */
public record ChatMessageResponse(
    Long id, // 主键标识。
    Long conversationId, // 关联会话标识。
    ChatMessageRole role, // role 字段。
    String content, // 主体内容。
    String thinkingContent, // 深度思考内容。
    Integer thinkingDuration, // 深度思考耗时。
    ChatMessageStatus status, // 当前状态值。
    String provider, // 提供方标识。
    String model, // 模型标识。
    String errorMessage, // 错误信息。
    LocalDateTime createdAt, // 创建时间。
    List<ChatAttachmentResponse> attachments, // 关联附件。
    List<String> skillCodes, // 本条消息所属运行绑定的技能编码。
    Integer userVote // 当前用户对该消息的投票值（1 点赞，-1 点踩，null 表示未投票）。
) {
}
