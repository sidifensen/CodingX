package com.codingx.common.support.ai;

/**
 * 表示模型请求后端执行的工具调用。
 * 业务约束：arguments 保留模型输出的原始 JSON 字符串，避免在模型层提前绑定到某个工具参数类型。
 *
 * @param callId 模型侧工具调用标识。
 * @param toolCode 工具编码。
 * @param arguments 工具参数 JSON 字符串。
 */
public record AiToolCall(
    String callId,
    String toolCode,
    String arguments
) {
}
