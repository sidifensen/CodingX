package com.codingx.chat.interfaces.response;

import java.time.LocalDateTime;

/**
 * 定义聊天附件响应结构。
 */
public record ChatAttachmentResponse(
    Long id,
    Long conversationId,
    Long messageId,
    String attachmentType,
    String fileName,
    String fileExt,
    String mimeType,
    Long fileSize,
    String previewUrl,
    String contentSummary,
    String status,
    LocalDateTime createdAt
) {
}
