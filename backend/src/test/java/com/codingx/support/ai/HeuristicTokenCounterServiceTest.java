package com.codingx.support.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证启发式 token 估算服务能给出稳定的预算结果。
 */
class HeuristicTokenCounterServiceTest {

    /**
     * 多条消息应按字符量累计 token 预算，并保留最小安全值。
     */
    @Test
    void estimateConversationTokensUsesCharacterHeuristic() {
        TokenCounterService service = new HeuristicTokenCounterService();

        int tokens = service.estimateConversationTokens(List.of(
            ChatMessage.create(1L, 1L, ChatMessageRole.USER, "你好，帮我介绍一下 OA 系统", ChatMessageStatus.COMPLETED, null, null, null),
            ChatMessage.create(2L, 1L, ChatMessageRole.ASSISTANT, "OA 系统用于统一办公流程。", ChatMessageStatus.COMPLETED, null, null, null)
        ));

        assertEquals(15, tokens);
    }
}
