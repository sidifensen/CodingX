package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示一次聊天请求对应的执行主链路记录。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatExecutionRun {

    private Long id;
    private Long conversationId;
    private Long requestMessageId;
    private Long responseMessageId;
    private Long taskId;
    private String intentCode;
    private String status;
    private String queueStatus;
    private Boolean searchEnabled;
    private Boolean artifactEnabled;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
