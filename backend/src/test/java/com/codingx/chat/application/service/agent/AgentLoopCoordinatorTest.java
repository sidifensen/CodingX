package com.codingx.chat.application.service.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.common.support.ai.AiToolCall;
import org.junit.jupiter.api.Test;

/**
 * 验证 Agent Loop 的确定性控制规则，避免聊天主流程散落重复的轮次与去重判断。
 */
class AgentLoopCoordinatorTest {

    /**
     * 工具轮次必须被限制在 1 到 20 之间，避免配置错误导致无限 ReAct 循环。
     */
    @Test
    void normalizeMaxRoundsShouldClampToSupportedRange() {
        AgentLoopCoordinator coordinator = new AgentLoopCoordinator();

        assertEquals(1, coordinator.normalizeMaxRounds(0));
        assertEquals(1, coordinator.normalizeMaxRounds(-5));
        assertEquals(8, coordinator.normalizeMaxRounds(8));
        assertEquals(20, coordinator.normalizeMaxRounds(100));
    }

    /**
     * 重复工具调用的判断只依赖工具编码和参数，callId 不参与，避免同参重复执行外部命令。
     */
    @Test
    void deduplicateKeyShouldIgnoreCallId() {
        AgentLoopCoordinator coordinator = new AgentLoopCoordinator();

        String firstKey = coordinator.deduplicateKey(new AiToolCall("call-a", "ReadFile", "{\"path\":\"README.md\"}"));
        String secondKey = coordinator.deduplicateKey(new AiToolCall("call-b", "ReadFile", "{\"path\":\"README.md\"}"));

        assertEquals(firstKey, secondKey);
    }

    /**
     * 没有工具调用时是正常完成；达到最后一轮后仍有工具调用时必须给出上限原因和中文错误。
     */
    @Test
    void shouldResolveCompletionReasonsForCommonLoopStates() {
        AgentLoopCoordinator coordinator = new AgentLoopCoordinator();

        AgentLoopResult noToolResult = coordinator.resolveRoundResult(0, 3, false, false, false);
        AgentLoopResult toolResult = coordinator.resolveRoundResult(0, 3, true, false, false);
        AgentLoopResult maxRoundResult = coordinator.resolveRoundResult(2, 3, true, false, false);
        AgentLoopResult toolErrorResult = coordinator.resolveRoundResult(0, 3, true, true, false);
        AgentLoopResult modelErrorResult = coordinator.resolveRoundResult(0, 3, false, false, true);

        assertEquals(AgentLoopCompletionReason.NO_TOOL_CALL, noToolResult.completionReason());
        assertEquals(AgentLoopCompletionReason.TOOL_CALLS_COMPLETED, toolResult.completionReason());
        assertEquals(AgentLoopCompletionReason.MAX_ROUNDS, maxRoundResult.completionReason());
        assertTrue(maxRoundResult.message().contains("本地工具调用轮次超过上限"));
        assertEquals(AgentLoopCompletionReason.TOOL_ERROR, toolErrorResult.completionReason());
        assertEquals(AgentLoopCompletionReason.MODEL_ERROR, modelErrorResult.completionReason());
    }
}
