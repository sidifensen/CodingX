package com.codingx.tool.application.service;

import java.util.Map;

/**
 * chat_tool 内置工具执行结果，统一返回可读内容与结构化元数据。
 *
 * @param toolCode 实际执行的工具编码。
 * @param content 工具返回给模型或前端展示的文本内容。
 * @param metadata 工具附加元数据，可为空；用于携带耗时、命中项等结构化信息。
 */
public record ChatToolExecutionResult(
    String toolCode, // 实际执行的工具编码。
    String content, // 工具返回给模型或前端展示的文本内容。
    Map<String, Object> metadata // 工具附加元数据，可为空；用于携带耗时、命中项等结构化信息。
) {
}

