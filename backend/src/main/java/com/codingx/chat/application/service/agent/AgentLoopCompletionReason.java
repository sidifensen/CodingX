package com.codingx.chat.application.service.agent;

/**
 * Agent Loop 单轮或整体运行的确定性结束原因。
 * 该枚举用于把“为什么继续/停止”从聊天主流程中抽出，方便后续 Web、CLI 和运行记录统一展示。
 */
public enum AgentLoopCompletionReason {

    /** 模型没有请求工具，本轮正文可作为最终回答。 */
    NO_TOOL_CALL,
    /** 模型请求的工具已执行并回灌，Agent Loop 可以进入下一轮。 */
    TOOL_CALLS_COMPLETED,
    /** 工具循环达到安全上限，必须停止继续调用模型。 */
    MAX_ROUNDS,
    /** 本地工具执行失败，外层应按失败消息收口。 */
    TOOL_ERROR,
    /** 模型流发生错误，外层应按 provider 错误收口。 */
    MODEL_ERROR,
    /** 用户主动取消或运行态守卫中断。 */
    USER_CANCELLED
}
