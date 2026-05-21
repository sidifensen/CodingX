package com.codingx.chat.application.service;

import com.codingx.chat.domain.model.ChatTraceRun;

/**
 * 定义管理端 Trace 列表单行视图，扩展 username 字段供前端直接展示。
 *
 * @param traceId 链路标识。
 * @param traceName 链路名称。
 * @param conversationId 会话标识。
 * @param taskId 任务标识。
 * @param userId 用户标识。
 * @param username 用户名。
 * @param status 运行状态。
 * @param errorMessage 错误信息。
 * @param durationMs 耗时毫秒。
 * @param startedAt 开始时间。
 * @param finishedAt 结束时间。
 */
public record AdminTraceRunListItemView(
    String traceId,
    String traceName,
    Long conversationId,
    Long taskId,
    Long userId,
    String username,
    String status,
    String errorMessage,
    Long durationMs,
    java.time.LocalDateTime startedAt,
    java.time.LocalDateTime finishedAt
) {

    /**
     * 将领域对象转换为列表项视图，附带用户名补全结果。
     *
     * @param traceRun 领域对象。
     * @param username 用户名。
     * @return 列表项视图。
     */
    public static AdminTraceRunListItemView from(ChatTraceRun traceRun, String username) {
        return new AdminTraceRunListItemView(
            traceRun.getTraceId(),
            traceRun.getTraceName(),
            traceRun.getConversationId(),
            traceRun.getTaskId(),
            traceRun.getUserId(),
            username,
            traceRun.getStatus(),
            traceRun.getErrorMessage(),
            traceRun.getDurationMs(),
            traceRun.getStartedAt(),
            traceRun.getFinishedAt()
        );
    }
}
