package com.codingx.chat.application.service.agent;

import cn.hutool.core.util.StrUtil;
import com.codingx.common.support.ai.AiToolCall;
import org.springframework.stereotype.Service;

/**
 * Agent Loop 确定性规则协调器。
 * 业务边界：该服务只处理轮次、安全上限、重复工具调用和完成原因，不直接调用模型或执行工具。
 */
@Service
public class AgentLoopCoordinator {

    /** Agent Loop 支持的最大安全轮次，防止配置错误造成无限工具循环。 */
    private static final int MAX_SUPPORTED_ROUNDS = 20;
    /** 工具轮次超过上限时返回给用户和日志的统一中文提示。 */
    private static final String MAX_ROUNDS_MESSAGE = "本地工具调用轮次超过上限，请收敛工具调用后重试";

    /**
     * 将运行时配置的工具轮次裁剪到安全范围。
     * @param configuredRounds 配置中心读取的轮次。
     * @return 1 到 20 之间的安全轮次。
     */
    public int normalizeMaxRounds(int configuredRounds) {
        return Math.max(1, Math.min(configuredRounds, MAX_SUPPORTED_ROUNDS));
    }

    /**
     * 构造工具调用去重键。
     * @param toolCall 模型请求的工具调用。
     * @return 基于工具编码和参数的稳定 key。
     */
    public String deduplicateKey(AiToolCall toolCall) {
        if (toolCall == null) {
            return "";
        }
        return StrUtil.blankToDefault(toolCall.toolCode(), "unknown") + "\n" + StrUtil.blankToDefault(toolCall.arguments(), "");
    }

    /**
     * 根据本轮状态解析 Agent Loop 下一步动作和结束原因。
     * @param zeroBasedRound 当前轮次，从 0 开始。
     * @param maxRounds 最大轮次。
     * @param hasToolCalls 本轮是否存在允许执行的工具调用。
     * @param toolError 本轮是否出现工具错误。
     * @param modelError 本轮是否出现模型错误。
     * @return 本轮判定结果。
     */
    public AgentLoopResult resolveRoundResult(
        int zeroBasedRound,
        int maxRounds,
        boolean hasToolCalls,
        boolean toolError,
        boolean modelError
    ) {
        // 步骤 1：模型错误和工具错误优先终止，避免继续追加误导性上下文。
        if (modelError) {
            return new AgentLoopResult(AgentLoopCompletionReason.MODEL_ERROR, false, null);
        }
        if (toolError) {
            return new AgentLoopResult(AgentLoopCompletionReason.TOOL_ERROR, false, null);
        }
        // 步骤 2：没有工具调用说明当前模型输出可作为最终回答，交由外层 flush 正文。
        if (!hasToolCalls) {
            return new AgentLoopResult(AgentLoopCompletionReason.NO_TOOL_CALL, false, null);
        }
        // 步骤 3：最后一轮仍有工具调用时停止循环，避免执行后无法继续生成最终答复。
        if (zeroBasedRound >= normalizeMaxRounds(maxRounds) - 1) {
            return new AgentLoopResult(AgentLoopCompletionReason.MAX_ROUNDS, false, MAX_ROUNDS_MESSAGE);
        }
        // 步骤 4：工具调用可以执行并回灌，外层进入下一轮模型生成。
        return new AgentLoopResult(AgentLoopCompletionReason.TOOL_CALLS_COMPLETED, true, null);
    }

    /**
     * @return 工具轮次超过上限时的统一中文提示。
     */
    public String maxRoundsMessage() {
        return MAX_ROUNDS_MESSAGE;
    }
}
