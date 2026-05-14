package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示一次聊天链路的根 Trace 记录。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatTraceRun {

    private Long id;
    private String traceId;
    private String traceName;
    private String entryMethod;
    private Long conversationId;
    private Long taskId;
    private Long userId;
    private String status;
    private String errorMessage;
    private Long durationMs;
    private String extraDataJson;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
