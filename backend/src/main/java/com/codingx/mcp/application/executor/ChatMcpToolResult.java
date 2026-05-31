package com.codingx.mcp.application.executor;

import java.util.Map;

/**
 * MCP 工具执行结果，统一承载可进入聊天上下文的文本与结构化元数据。
 * @param toolId 实际执行的 MCP 工具标识。
 * @param content 工具返回的可读文本内容，会进入聊天上下文或管理端探测结果。
 * @param metadata 工具附加结构化元数据，可为空；用于携带位置、耗时或命中记录等信息。
 */
public record ChatMcpToolResult(
    String toolId, // 实际执行的 MCP 工具标识。
    String content, // 工具返回的可读文本内容，会进入聊天上下文或管理端探测结果。
    Map<String, Object> metadata // 工具附加结构化元数据，可为空；用于携带位置、耗时或命中记录等信息。
) {
}

