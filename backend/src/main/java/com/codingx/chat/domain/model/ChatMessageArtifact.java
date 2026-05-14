package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示聊天执行过程中生成的文件产物记录。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMessageArtifact {

    private Long id;
    private Long runId;
    private Long messageId;
    private Long conversationId;
    private String artifactType;
    private String name;
    private String mimeType;
    private String storagePath;
    private String contentPreview;
    private String metadataJson;
    private LocalDateTime createdAt;
}
