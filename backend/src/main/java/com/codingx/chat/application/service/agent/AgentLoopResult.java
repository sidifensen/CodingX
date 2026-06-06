package com.codingx.chat.application.service.agent;

/**
 * Agent Loop 确定性规则的判断结果。
 *
 * @param completionReason 当前判断对应的完成或继续原因。
 * @param shouldContinue true 表示可继续下一轮模型生成或工具执行。
 * @param message 面向上层的中文提示，可为空；错误和上限场景会使用该字段。
 */
public record AgentLoopResult(
    AgentLoopCompletionReason completionReason, // 当前判断对应的完成或继续原因。
    boolean shouldContinue, // true 表示可继续下一轮模型生成或工具执行。
    String message // 面向上层的中文提示，可为空；错误和上限场景会使用该字段。
) {
}
