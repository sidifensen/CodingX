package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示用户上传并用于聊天上下文的附件记录。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatAttachment {

    private Long id;
    private Long runId;
    private Long conversationId;
    private Long messageId;
    private Long uploadedBy;
    private String attachmentType;
    private String fileName;
    private String fileExt;
    private String mimeType;
    private Long fileSize;
    private String storageKey;
    private String previewUrl;
    private String contentSummary;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
