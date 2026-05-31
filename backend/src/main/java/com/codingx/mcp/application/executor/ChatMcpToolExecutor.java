package com.codingx.mcp.application.executor;

import java.util.Map;

/**
 * 单个 MCP 工具执行器契约，负责把用户问题转换为工具结果和可选进度事件。
 */
public interface ChatMcpToolExecutor {

    /**
     * 返回当前执行器负责的工具标识。
     * @return 工具标识。
     */
    String toolId();

    /**
     * 执行指定问题的工具调用。
     * @param question 用户问题或已经改写后的工具输入。
     * @return 工具结果。
     */
    ChatMcpToolResult execute(String question);

    /**
     * 执行指定问题的工具调用，并在真实阶段上报进度。
     * <p>
     * 关键约束：
     * 1. 默认实现保持向后兼容，不要求所有既有工具立刻改造
     * 2. 需要真实进度的工具可覆盖本方法并主动回调 listener
     *
     * @param question 用户问题或已经改写后的工具输入。
     * @param progressListener 进度回调监听器，可为空；为空时实现应避免抛异常。
     * @return 工具结果。
     */
    default ChatMcpToolResult execute(String question, ChatMcpProgressListener progressListener) {
        return execute(question);
    }

    /**
     * 安全上报进度，屏蔽空监听器与空文案场景，避免调用点重复判空。
     *
     * @param progressListener 进度监听器，可为空。
     * @param stage 阶段标识，建议使用短英文标识。
     * @param message 阶段文案，空值或空白值会被忽略。
     * @param detail 阶段详情，空值会被转换为空 Map。
     */
    default void emitProgress(
        ChatMcpProgressListener progressListener,
        String stage,
        String message,
        Map<String, Object> detail
    ) {
        if (progressListener == null || message == null || message.isBlank()) {
            return;
        }
        progressListener.onProgress(stage, message, detail == null ? Map.of() : detail);
    }
}

