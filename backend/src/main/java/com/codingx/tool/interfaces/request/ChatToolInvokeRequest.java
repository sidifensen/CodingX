package com.codingx.tool.interfaces.request;

/**
 * 定义工具手工调用请求。
 * @param question 调用参数，支持自然语言或 JSON。
 * @param confirmHighRisk 是否确认执行高风险命令。
 */
public record ChatToolInvokeRequest(
    String question, // 手工调用工具的输入内容，支持自然语言或 JSON 参数。
    Boolean confirmHighRisk // 是否确认执行高风险工具调用，true 表示用户已明确确认。
) {
}

