package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示 Trace 链路中的单个节点记录。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatTraceNode {

    private Long id;
    private String traceId;
    private String nodeId;
    private String parentNodeId;
    private Integer depth;
    private String nodeType;
    private String nodeName;
    private String className;
    private String methodName;
    private String status;
    private String errorMessage;
    private Long durationMs;
    private String extraDataJson;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
