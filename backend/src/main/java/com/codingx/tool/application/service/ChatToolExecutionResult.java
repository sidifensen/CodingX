package com.codingx.tool.application.service;

import java.util.Map;

/**
 * 表示一次 chat_tool 内置工具执行结果。
 *
 * @param toolCode 工具编码。
 * @param content 工具返回文本。
 * @param metadata 工具附加元数据。
 */
public record ChatToolExecutionResult(
    String toolCode,
    String content,
    Map<String, Object> metadata
) {
}

