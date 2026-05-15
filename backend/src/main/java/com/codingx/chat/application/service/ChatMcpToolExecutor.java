package com.codingx.chat.application.service;

/**
 * 定义单个 MCP 工具执行器的最小契约。
 */
public interface ChatMcpToolExecutor {

    /**
     * 返回当前执行器负责的工具标识。
     * @return 工具标识。
     */
    String toolId();

    /**
     * 执行指定问题的工具调用。
     * @param question 用户问题。
     * @return 工具结果。
     */
    ChatMcpToolResult execute(String question);
}
