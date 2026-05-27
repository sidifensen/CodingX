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

    /**
     * 创建运行中的根 Trace，避免聊天入口依赖 Lombok 生成的 builder 内部类。
     * @param traceId 链路标识。
     * @param traceName 链路名称。
     * @param conversationId 会话标识。
     * @param userId 用户标识。
     * @return 运行中的根 Trace。
     */
    public static ChatTraceRun createRunning(String traceId, String traceName, Long conversationId, Long userId) {
        ChatTraceRun traceRun = new ChatTraceRun();
        traceRun.traceId = traceId;
        traceRun.traceName = traceName;
        traceRun.conversationId = conversationId;
        traceRun.userId = userId;
        traceRun.status = "RUNNING";
        return traceRun;
    }

    /**
     * 从持久化数据恢复领域对象，保持仓储层不依赖 Lombok 生成代码。
     */
    public static ChatTraceRun restore(
        Long id,
        String traceId,
        String traceName,
        String entryMethod,
        Long conversationId,
        Long taskId,
        Long userId,
        String status,
        String errorMessage,
        Long durationMs,
        String extraDataJson,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer deleted
    ) {
        ChatTraceRun traceRun = new ChatTraceRun();
        traceRun.id = id;
        traceRun.traceId = traceId;
        traceRun.traceName = traceName;
        traceRun.entryMethod = entryMethod;
        traceRun.conversationId = conversationId;
        traceRun.taskId = taskId;
        traceRun.userId = userId;
        traceRun.status = status;
        traceRun.errorMessage = errorMessage;
        traceRun.durationMs = durationMs;
        traceRun.extraDataJson = extraDataJson;
        traceRun.startedAt = startedAt;
        traceRun.finishedAt = finishedAt;
        traceRun.createdAt = createdAt;
        traceRun.updatedAt = updatedAt;
        traceRun.deleted = deleted;
        return traceRun;
    }

    /**
     * 补齐新建 Trace 的持久化元信息。
     */
    public ChatTraceRun withStartMetadata(
        Long id,
        String entryMethod,
        LocalDateTime startedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer deleted
    ) {
        ChatTraceRun traceRun = copy();
        traceRun.id = id;
        traceRun.entryMethod = entryMethod;
        traceRun.startedAt = startedAt;
        traceRun.createdAt = createdAt;
        traceRun.updatedAt = updatedAt;
        traceRun.deleted = deleted;
        return traceRun;
    }

    /**
     * 生成收口后的 Trace 副本，保留原始链路信息并更新终态字段。
     */
    public ChatTraceRun finish(Long taskId, String status, String errorMessage, LocalDateTime finishedAt, Long durationMs) {
        ChatTraceRun traceRun = copy();
        traceRun.taskId = taskId;
        traceRun.status = status;
        traceRun.errorMessage = errorMessage;
        traceRun.finishedAt = finishedAt;
        traceRun.updatedAt = finishedAt;
        traceRun.durationMs = durationMs;
        return traceRun;
    }

    private ChatTraceRun copy() {
        return restore(
            id,
            traceId,
            traceName,
            entryMethod,
            conversationId,
            taskId,
            userId,
            status,
            errorMessage,
            durationMs,
            extraDataJson,
            startedAt,
            finishedAt,
            createdAt,
            updatedAt,
            deleted
        );
    }
}
