package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示最终展示给用户的参考来源记录。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatMessageReference {

    private Long id;
    private Long runId;
    private Long messageId;
    private Long conversationId;
    private String sourceType;
    private String title;
    private String url;
    private String siteName;
    private String snippet;
    private Integer rankNo;
    private String metadataJson;
    private LocalDateTime createdAt;
}
