package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.chat.domain.model.ChatMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证会话摘要服务的触发阈值判断。
 */
class ConversationDigestServiceTest {

    /**
     * 消息数量达到阈值后应触发摘要压缩。
     */
    @Test
    void shouldSummarizeWhenMessageCountReachedThreshold() {
        ConversationDigestService service = new ConversationDigestService(4);

        assertFalse(service.shouldSummarize(List.of(
            ChatMessage.userMessage(1L, "1"),
            ChatMessage.userMessage(1L, "2"),
            ChatMessage.userMessage(1L, "3")
        )));

        assertTrue(service.shouldSummarize(List.of(
            ChatMessage.userMessage(1L, "1"),
            ChatMessage.userMessage(1L, "2"),
            ChatMessage.userMessage(1L, "3"),
            ChatMessage.userMessage(1L, "4")
        )));
    }
}
