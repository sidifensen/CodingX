package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.port.AiChatClient;
import com.codingx.common.error.ErrorMessageCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证会话标题生成服务的最小行为。
 */
@ExtendWith(MockitoExtension.class)
class ConversationTitleServiceTest {

    /**
     * 模型客户端依赖。
     */
    @Mock
    private AiChatClient aiChatClient;

    /**
     * 被测服务。
     */
    @InjectMocks
    private ConversationTitleService conversationTitleService;

    /**
     * 标题生成应优先基于首条用户消息收敛出简短标题。
     */
    @Test
    void generateTitleUsesFirstUserMessage() {
        ChatConversation conversation = ChatConversation.create(
            1L,
            ErrorMessageCatalog.CHAT_CONVERSATION_DEFAULT_TITLE,
            1001L,
            ChatConversationStatus.ACTIVE
        );
        List<ChatMessage> messages = List.of(ChatMessage.userMessage(1L, "帮我整理一份 AI 搜索重构计划"));
        when(aiChatClient.generateTitle(messages)).thenReturn("AI搜索重构计划");

        String title = conversationTitleService.generateTitle(conversation, messages);

        assertEquals("AI搜索重构计划", title);
    }
}

