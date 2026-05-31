package com.codingx.tool.application.service;

import java.util.List;

/**
 * chat_tool 内置工具执行器契约，屏蔽具体本地工具实现差异。
 */
public interface ChatToolExecutor {

    /**
     * 返回当前执行器支持的工具编码集合。
     * @return 工具编码列表。
     */
    List<String> toolCodes();

    /**
     * 执行指定工具。
     * @param toolCode 工具编码，必须属于 toolCodes 返回的集合。
     * @param question 测试问题、自然语言指令或结构化 JSON 参数。
     * @return 工具执行结果。
     */
    ChatToolExecutionResult execute(String toolCode, String question);
}

