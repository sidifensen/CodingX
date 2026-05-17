package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.config.ChatMemoryProperties;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatConversationSummary;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.repository.ChatConversationSummaryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证会话摘要服务在压缩触发与入模上下文构造时的核心行为。
 */
@ExtendWith(MockitoExtension.class)
class ConversationSummaryServiceTest {

    @Mock
    private ChatConversationSummaryRepository chatConversationSummaryRepository;

    /**
     * 达到阈值时应生成摘要，并把 lastMessageId 指向被压缩区间末尾消息。
     */
    @Test
    void refreshSummaryIfNeededShouldPersistSummaryBeforeRecentWindow() {
        ChatMemoryProperties memoryProperties = new ChatMemoryProperties();
        memoryProperties.setSummaryEnabled(true);
        memoryProperties.setSummaryTriggerMessages(4);
        memoryProperties.setHistoryKeepTurns(1);
        memoryProperties.setSummaryMaxCharacters(4000);
        ConversationSummaryService service = new ConversationSummaryService(
            new ConversationDigestService(memoryProperties),
            chatConversationSummaryRepository,
            memoryProperties
        );
        ChatConversation conversation = ChatConversation.create(100L, "会话", 200L, ChatConversationStatus.ACTIVE);
        List<ChatMessage> history = List.of(
            ChatMessage.userMessage(100L, "u1"),
            ChatMessage.assistantMessage(100L, "a1", com.codingx.chat.domain.model.ChatMessageStatus.COMPLETED, null, null, null),
            ChatMessage.userMessage(100L, "u2"),
            ChatMessage.assistantMessage(100L, "a2", com.codingx.chat.domain.model.ChatMessageStatus.COMPLETED, null, null, null),
            ChatMessage.userMessage(100L, "u3"),
            ChatMessage.assistantMessage(100L, "a3", com.codingx.chat.domain.model.ChatMessageStatus.COMPLETED, null, null, null)
        );
        when(chatConversationSummaryRepository.findLatestByConversationId(100L)).thenReturn(Optional.empty());

        Optional<ChatConversationSummary> result = service.refreshSummaryIfNeeded(conversation, history);

        assertTrue(result.isPresent());
        ArgumentCaptor<ChatConversationSummary> captor = ArgumentCaptor.forClass(ChatConversationSummary.class);
        verify(chatConversationSummaryRepository).save(captor.capture());
        ChatConversationSummary persisted = captor.getValue();
        assertEquals(history.get(history.size() - 3).getId(), persisted.getLastMessageId());
        assertTrue(persisted.getContent().contains("user: u1"));
        assertTrue(persisted.getContent().contains("assistant: a2"));
        assertFalse(persisted.getContent().contains("assistant: a3"));
    }

    /**
     * 构造入模历史时，应把摘要作为 system 消息放在首位，并仅保留摘要后最近窗口原文。
     */
    @Test
    void buildModelHistoryShouldUseSummaryAndRecentWindow() {
        ChatMemoryProperties memoryProperties = new ChatMemoryProperties();
        memoryProperties.setSummaryEnabled(true);
        memoryProperties.setSummaryTriggerMessages(4);
        memoryProperties.setHistoryKeepTurns(1);
        memoryProperties.setSummaryMaxCharacters(4000);
        ConversationSummaryService service = new ConversationSummaryService(
            new ConversationDigestService(memoryProperties),
            chatConversationSummaryRepository,
            memoryProperties
        );
        List<ChatMessage> history = List.of(
            ChatMessage.userMessage(100L, "u1"),
            ChatMessage.assistantMessage(100L, "a1", com.codingx.chat.domain.model.ChatMessageStatus.COMPLETED, null, null, null),
            ChatMessage.userMessage(100L, "u2"),
            ChatMessage.assistantMessage(100L, "a2", com.codingx.chat.domain.model.ChatMessageStatus.COMPLETED, null, null, null),
            ChatMessage.userMessage(100L, "u3"),
            ChatMessage.assistantMessage(100L, "a3", com.codingx.chat.domain.model.ChatMessageStatus.COMPLETED, null, null, null)
        );
        ChatConversationSummary summary = ChatConversationSummary.builder()
            .id(300L)
            .conversationId(100L)
            .userId(200L)
            .lastMessageId(history.get(3).getId())
            .content("历史摘要")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .deleted(0)
            .build();
        when(chatConversationSummaryRepository.findLatestByConversationId(100L)).thenReturn(Optional.of(summary));

        List<ChatMessage> aiHistory = service.buildModelHistory(100L, history);

        assertEquals(3, aiHistory.size());
        assertEquals(ChatMessageRole.SYSTEM, aiHistory.getFirst().getRole());
        assertTrue(aiHistory.getFirst().getContent().contains("历史摘要"));
        assertEquals("u3", aiHistory.get(1).getContent());
        assertEquals("a3", aiHistory.get(2).getContent());
    }
}
