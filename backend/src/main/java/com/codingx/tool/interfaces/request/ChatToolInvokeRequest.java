package com.codingx.tool.interfaces.request;

/**
 * 定义工具手工调用请求。
 * @param question 调用参数，支持自然语言或 JSON。
 * @param confirmHighRisk 是否确认执行高风险命令。
 */
public record ChatToolInvokeRequest(
    String question,
    Boolean confirmHighRisk
) {
}

