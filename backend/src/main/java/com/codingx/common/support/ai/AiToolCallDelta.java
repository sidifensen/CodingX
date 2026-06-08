package com.codingx.common.support.ai;

/**
 * 表示模型流式生成中的工具调用参数片段。
 * 业务意图：大文件写入时 function arguments 可能持续生成数十秒，应用层需要在完整 tool_call 到达前拿到进度，
 * 让前端先展示“正在编辑文件”和临时 diff，而不是一直停留在“正在生成回答”。
 *
 * @param callId 模型侧工具调用标识；provider 未返回时由解析器生成稳定兜底值。
 * @param toolCode 当前已识别的工具编码；为空时不会向应用层派发。
 * @param argumentsDelta 本次新增的参数片段，可为空字符串。
 * @param accumulatedArguments 当前工具调用已经累积到的完整参数前缀。
 */
public record AiToolCallDelta(
    String callId, // 模型侧工具调用标识；同一工具调用的 progress/start/complete 必须保持一致。
    String toolCode, // 工具编码，来自 function.name，用于前端决定是否构造文件编辑预览。
    String argumentsDelta, // 本次新增参数片段，用于诊断 provider 分片节奏。
    String accumulatedArguments // 已累积参数前缀，允许不是完整 JSON。
) {
}
