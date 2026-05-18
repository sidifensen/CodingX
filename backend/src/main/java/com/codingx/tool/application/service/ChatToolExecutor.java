package com.codingx.tool.application.service;

import java.util.List;

/**
 * 定义 chat_tool 内置工具执行器最小契约。
 */
public interface ChatToolExecutor {

    /**
     * 返回当前执行器支持的工具编码集合。
     * @return 工具编码列表。
     */
    List<String> toolCodes();

    /**
     * 执行指定工具。
     * @param toolCode 工具编码。
     * @param question 测试问题或指令。
     * @return 工具执行结果。
     */
    ChatToolExecutionResult execute(String toolCode, String question);
}

