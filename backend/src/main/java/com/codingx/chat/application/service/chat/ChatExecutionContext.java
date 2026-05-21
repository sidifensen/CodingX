package com.codingx.chat.application.service;

import java.util.Optional;

/**
 * 维护聊天执行的线程级上下文，用于贯穿 runId 等运行期信息。
 */
public final class ChatExecutionContext {

    private static final ThreadLocal<Long> CURRENT_RUN_ID = new ThreadLocal<>();

    private ChatExecutionContext() {
    }

    /**
     * 绑定当前执行记录 ID。
     * @param runId 执行记录标识。
     */
    public static void start(Long runId) {
        CURRENT_RUN_ID.set(runId);
    }

    /**
     * 获取当前执行记录 ID。
     * @return 执行记录标识。
     */
    public static Optional<Long> currentRunId() {
        return Optional.ofNullable(CURRENT_RUN_ID.get());
    }

    /**
     * 清理当前线程上的执行上下文。
     */
    public static void clear() {
        CURRENT_RUN_ID.remove();
    }
}
