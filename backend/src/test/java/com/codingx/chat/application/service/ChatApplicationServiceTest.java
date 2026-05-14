package com.codingx.chat.application.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.chat.domain.service.AiChatClient;
import com.codingx.chat.domain.service.ChatStreamPublisher;
import com.codingx.chat.infrastructure.runtime.ChatRunControlService;
import java.util.concurrent.atomic.AtomicInteger;
import com.codingx.common.exception.ForbiddenException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证 ChatApplicationService 的关键场景。
 */
@ExtendWith(MockitoExtension.class)
class ChatApplicationServiceTest {

    /**
     * ChatConversationRepository 依赖。
     */
    @Mock
    private ChatConversationRepository chatConversationRepository;

    /**
     * ChatMessageRepository 依赖。
     */
    @Mock
    private ChatMessageRepository chatMessageRepository;

    /**
     * AiChatClient 依赖。
     */
    @Mock
    private AiChatClient aiChatClient;

    /**
     * ChatStreamPublisher 依赖。
     */
    @Mock
    private ChatStreamPublisher chatStreamPublisher;

    /**
     * ConversationTitleService 依赖。
     */
    @Mock
    private ConversationTitleService conversationTitleService;

    /**
     * ConversationSummaryService 依赖。
     */
    @Mock
    private ConversationSummaryService conversationSummaryService;

    /**
     * ConversationRewriteService 依赖。
     */
    @Mock
    private ConversationRewriteService conversationRewriteService;

    /**
     * ConversationIntentService 依赖。
     */
    @Mock
    private ConversationIntentService conversationIntentService;

    /**
     * ChatRuntimeGuardService 依赖。
     */
    @Mock
    private ChatRuntimeGuardService chatRuntimeGuardService;

    /**
     * ChatApplicationService 依赖。
     */
    @InjectMocks
    private ChatApplicationService chatApplicationService;

    /**
     * 发送 sendMessagePersistsUserAndAssistantMessages 处理的消息或请求。
     */
    @Test
    void sendMessagePersistsUserAndAssistantMessages() {

        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        List<ChatMessage> history = new ArrayList<>();
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(history);
        when(conversationTitleService.generateTitle(org.mockito.ArgumentMatchers.eq(conversation), any())).thenReturn("AI搜索重构计划");
        when(conversationRewriteService.rewrite(any(), any())).thenReturn("Hi");
        when(conversationIntentService.route("Hi")).thenReturn(new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null));
        doAnswer(invocation -> {

            AiChatClient.StreamHandler handler = invocation.getArgument(1);
            handler.onDelta("Hello");
            handler.onDelta(" world");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), any());
        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "Hi"), 1002L);        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        verify(chatRuntimeGuardService).ensureAccepted(1L);
        verify(conversationTitleService).generateTitle(org.mockito.ArgumentMatchers.eq(conversation), any());
        verify(conversationSummaryService).refreshSummaryIfNeeded(org.mockito.ArgumentMatchers.eq(conversation), any());
        verify(chatStreamPublisher).publishAssistantCompleted(1L, "Hello world", "AI搜索重构计划");
        assertEquals(ChatMessageRole.ASSISTANT, captor.getAllValues().get(1).getRole());
        assertEquals("Hello world", captor.getAllValues().get(1).getContent());
        assertEquals(ChatMessageStatus.COMPLETED, captor.getAllValues().get(1).getStatus());
        assertEquals("AI搜索重构计划", conversation.getTitle());
    }

    /**
     * 发送 sendMessageRejectsNonOwnerConversation 处理的消息或请求。
     */
    @Test
    void sendMessageRejectsNonOwnerConversation() {

        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        ForbiddenException exception = assertThrows(
            ForbiddenException.class,
            () -> chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "Hi"), 2001L)

        );
        assertEquals("You cannot access this conversation", exception.getMessage());
    }

    /**
     * 队列拒绝时应直接终止，不再进入模型调用链路。
     */
    @Test
    void sendMessageStopsWhenRuntimeGuardRejectsConversation() {
        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        org.mockito.Mockito.doThrow(new IllegalStateException("Conversation rejected: busy"))
            .when(chatRuntimeGuardService).ensureAccepted(1L);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "Hi"), 1002L)
        );

        assertEquals("Conversation rejected: busy", exception.getMessage());
        org.mockito.Mockito.verifyNoInteractions(aiChatClient);
    }

    /**
     * 会话被取消后应以 CANCELLED 状态落库，而不是继续完成或失败收口。
     */
    @Test
    void sendMessagePersistsCancelledAssistantMessageWhenRuntimeMarkedCancelled() {
        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(conversationRewriteService.rewrite(any(), any())).thenReturn("Hi");
        when(conversationIntentService.route("Hi")).thenReturn(new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null));
        AtomicInteger cancelChecks = new AtomicInteger();
        when(chatRuntimeGuardService.isCancelled(1L)).thenAnswer(invocation -> cancelChecks.incrementAndGet() > 1);
        doAnswer(invocation -> {
            AiChatClient.StreamHandler handler = invocation.getArgument(1);
            handler.onDelta("partial");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), any());

        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "Hi"), 1002L);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals(ChatMessageStatus.CANCELLED, captor.getAllValues().get(1).getStatus());
        assertEquals("partial", captor.getAllValues().get(1).getContent());
    }
}
