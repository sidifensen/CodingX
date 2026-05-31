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
    String callId, // 模型侧工具调用唯一标识，可为空；为空时解析器会生成兜底 ID。
    String toolCode, // 工具编码，来自模型返回的 function.name，用于匹配后端工具定义。
    String arguments // 工具参数原始 JSON 字符串，可为空字符串；由工具执行层负责解析和校验。
) {
}
