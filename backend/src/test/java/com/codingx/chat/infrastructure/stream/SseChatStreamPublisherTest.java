package com.codingx.chat.infrastructure.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 验证聊天 SSE 发布器的事件载荷，确保前端可直接消费完成态上下文。
 */
class SseChatStreamPublisherTest {

    /**
     * finish 事件必须带上已落库助手消息 ID，避免前端等待历史回放后才能启用消息操作。
     */
    @Test
    void publishAssistantCompletedShouldIncludeAssistantMessageId() {
        ChatSseRegistry registry = org.mockito.Mockito.mock(ChatSseRegistry.class);
        SseChatStreamPublisher publisher = new SseChatStreamPublisher(registry);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

        publisher.publishAssistantCompleted(1L, 102L, "回答内容", "会话标题");

        verify(registry).publish(
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.eq("finish"),
            payloadCaptor.capture()
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertEquals(1L, payload.get("conversationId"));
        assertEquals(102L, payload.get("assistantMessageId"));
        assertEquals("回答内容", payload.get("content"));
        assertEquals("会话标题", payload.get("title"));
    }
}
