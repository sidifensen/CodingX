package com.codingx.chat.interfaces.request;

/**
 * 定义工具手工调用请求。
 * @param question 调用参数，支持自然语言或 JSON。
 */
public record ChatToolInvokeRequest(
    String question
) {
}
