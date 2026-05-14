package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.chat.domain.service.AiChatClient;
import com.codingx.chat.domain.service.ChatStreamPublisher;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证聊天主链路会按意图决策执行直答或澄清。
 */
@ExtendWith(MockitoExtension.class)
class ChatApplicationIntentFlowTest {

    @Mock
    private ChatConversationRepository chatConversationRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatExecutionRunRepository chatExecutionRunRepository;

    @Mock
    private AiChatClient aiChatClient;

    @Mock
    private ChatStreamPublisher chatStreamPublisher;

    @Mock
    private ChatRuntimeGuardService chatRuntimeGuardService;

    @Mock
    private ConversationTitleService conversationTitleService;

    @Mock
    private ConversationSummaryService conversationSummaryService;

    @Mock
    private ConversationRewriteService conversationRewriteService;

    @Mock
    private ConversationIntentService conversationIntentService;

    @InjectMocks
    private ChatApplicationService chatApplicationService;

    /**
     * 歧义问题应直接返回澄清消息，并跳过模型调用。
     */
    @Test
    void sendMessageReturnsClarificationWhenIntentIsAmbiguous() {
        ChatConversation conversation = ChatConversation.create(1L, "New Conversation", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(conversationRewriteService.rewrite(any(), any())).thenReturn("这个要怎么改");
        when(conversationIntentService.route("这个要怎么改")).thenReturn(
            new ConversationIntentDecision("clarify.ambiguity", ConversationIntentAction.CLARIFY, "请补充你指的是哪一部分")
        );

        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "这个要怎么改"), 1002L);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals(ChatMessageStatus.COMPLETED, captor.getAllValues().get(1).getStatus());
        assertEquals("请补充你指的是哪一部分", captor.getAllValues().get(1).getContent());
        verify(chatStreamPublisher).publishAssistantCompleted(1L, "请补充你指的是哪一部分", "New Conversation");
        org.mockito.Mockito.verifyNoInteractions(aiChatClient);
    }
}
