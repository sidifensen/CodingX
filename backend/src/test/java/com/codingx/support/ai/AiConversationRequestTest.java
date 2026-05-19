package com.codingx.common.support.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codingx.chat.domain.model.ChatMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证统一模型请求对象会固化模型层所需的关键约束。
 */
class AiConversationRequestTest {

    /**
     * 请求对象需要保留消息、模型和思考配置，供路由层直接消费。
     */
    @Test
    void builderCreatesRequestWithExpectedDefaults() {
        AiConversationRequest request = AiConversationRequest.builder()
            .messages(List.of(ChatMessage.userMessage(1L, "请总结这个需求")))
            .preferredModel("deepseek-chat")
            .stream(true)
            .thinkingEnabled(true)
            .build();

        assertEquals(1, request.messages().size());
        assertEquals("deepseek-chat", request.preferredModel());
        assertEquals(true, request.stream());
        assertEquals(true, request.thinkingEnabled());
    }

    /**
     * 没有消息的请求不应放行到 provider 层。
     */
    @Test
    void builderRejectsEmptyMessages() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> AiConversationRequest.builder()
            .messages(List.of())
            .preferredModel("deepseek-chat")
            .stream(true)
            .build());

        assertEquals("AI conversation messages are required", exception.getMessage());
    }
}
