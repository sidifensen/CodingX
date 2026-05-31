package com.codingx.admin.application.service;

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
    String traceId, // 链路标识，关联一次聊天或任务执行链路。
    String traceName, // 链路展示名称，可为空。
    Long conversationId, // 关联会话主键，可为空。
    Long taskId, // 关联后台任务主键，可为空。
    Long userId, // 发起链路的用户主键，可为空。
    String username, // 用户名展示值，用户缺失时由服务层兜底。
    String status, // 链路状态编码，例如 RUNNING、SUCCESS、FAILED。
    String errorMessage, // 链路失败时的错误说明，非失败状态可为空。
    Long durationMs, // 链路耗时，单位毫秒，未结束时可为空。
    java.time.LocalDateTime startedAt, // 链路开始时间，可为空。
    java.time.LocalDateTime finishedAt // 链路结束时间，未结束时可为空。
) {

    /**
     * 将领域对象转换为列表项视图，附带用户名补全结果。
     *
     * @param traceRun 领域对象。
     * @param username 用户名。
     * @return 列表项视图。
     */
    public static AdminTraceRunListItemView from(ChatTraceRun traceRun, String username) {
        // 步骤 1：TraceRun 领域对象提供链路本体字段，用户名由管理服务按 userId 额外补齐。
        // 步骤 2：列表视图保持只读投影，不在这里查询仓储或修改领域对象状态。
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
