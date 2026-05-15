package com.codingx.chat.application.service;

import java.util.Map;

/**
 * 表示一次 MCP 工具执行结果。
 * @param toolId 工具标识。
 * @param content 工具返回的可读内容。
 * @param metadata 工具附加元数据。
 */
public record ChatMcpToolResult(
    String toolId,
    String content,
    Map<String, Object> metadata
) {
}
