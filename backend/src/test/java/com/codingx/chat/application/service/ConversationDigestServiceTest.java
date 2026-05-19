package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.model.ChatMessage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证会话摘要服务的触发阈值判断。
 */
@ExtendWith(MockitoExtension.class)
class ConversationDigestServiceTest {

    @Mock
    private RuntimeSettingService runtimeSettingService;

    /**
     * 消息数量达到阈值后应触发摘要压缩。
     */
    @Test
    void shouldSummarizeWhenMessageCountReachedThreshold() {
        when(runtimeSettingService.summaryTriggerMessages()).thenReturn(4);
        ConversationDigestService service = new ConversationDigestService(runtimeSettingService);

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

    /**
     * 配置化阈值应生效，避免阈值固定导致环境不可调。
     */
    @Test
    void shouldUseThresholdFromRuntimeSettings() {
        when(runtimeSettingService.summaryTriggerMessages()).thenReturn(3);
        ConversationDigestService service = new ConversationDigestService(runtimeSettingService);

        assertFalse(service.shouldSummarize(List.of(
            ChatMessage.userMessage(1L, "1"),
            ChatMessage.userMessage(1L, "2")
        )));

        assertTrue(service.shouldSummarize(List.of(
            ChatMessage.userMessage(1L, "1"),
            ChatMessage.userMessage(1L, "2"),
            ChatMessage.userMessage(1L, "3")
        )));
    }
}
