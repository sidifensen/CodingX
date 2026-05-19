package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatExecutionRun;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.chat.domain.repository.ChatIntentNodeRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.chat.domain.service.AiChatClient;
import com.codingx.chat.domain.service.ChatStreamPublisher;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
     * ChatExecutionRunRepository 依赖。
     */
    @Mock
    private ChatExecutionRunRepository chatExecutionRunRepository;

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
     * PromptTemplateLoader 依赖。
     */
    @Mock
    private PromptTemplateLoader promptTemplateLoader;

    @Mock
    private ChatIntentNodeRepository chatIntentNodeRepository;

    @Mock
    private com.codingx.common.support.ai.TokenCounterService tokenCounterService;

    @Mock
    private com.codingx.common.support.ai.LlmResponseCleaner llmResponseCleaner;

    @Mock
    private java.util.concurrent.ExecutorService searchExecutor;

    @Mock
    private ChatMcpRepository chatMcpRepository;

    @Mock
    private ChatAttachmentService chatAttachmentService;

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
     * 为当前测试线程绑定运行时 runId，避免单测绕过异步入口后丢失主链路上下文。
     * @return 当前测试绑定的 runId。
     */
    private Long bindRunContext() {
        Long runId = 9001001L;
        ChatExecutionContext.start(runId);
        return runId;
    }

    /**
     * 发送 sendMessagePersistsUserAndAssistantMessages 处理的消息或请求。
     */
    @Test
    void sendMessagePersistsUserAndAssistantMessages() {
        Long runId = bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        List<ChatMessage> history = new ArrayList<>();
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(history);
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(conversationTitleService.generateTitle(org.mockito.ArgumentMatchers.eq(conversation), any())).thenReturn("AI搜索重构计划");
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("Hi", false, List.of("Hi"))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationIntentService.route("Hi", false)).thenReturn(new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null));
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        doAnswer(invocation -> {

            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onDelta("Hello");
            handler.onDelta(" world");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), org.mockito.ArgumentMatchers.anyBoolean(), any());
        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "Hi", false), 1002L);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        ArgumentCaptor<ChatExecutionRun> runCaptor = ArgumentCaptor.forClass(ChatExecutionRun.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        verify(chatExecutionRunRepository).save(runCaptor.capture());
        verify(chatRuntimeGuardService).ensureAccepted(1L);
        verify(conversationTitleService).generateTitle(org.mockito.ArgumentMatchers.eq(conversation), any());
        verify(conversationSummaryService).buildModelHistory(org.mockito.ArgumentMatchers.eq(1L), any());
        verify(conversationSummaryService).refreshSummaryIfNeeded(org.mockito.ArgumentMatchers.eq(conversation), any());
        verify(chatStreamPublisher).publishAssistantCompleted(1L, "Hello world", "AI搜索重构计划");
        assertEquals(ChatMessageRole.ASSISTANT, captor.getAllValues().get(1).getRole());
        assertEquals("Hello world", captor.getAllValues().get(1).getContent());
        assertEquals(ChatMessageStatus.COMPLETED, captor.getAllValues().get(1).getStatus());
        assertEquals(runId, runCaptor.getValue().getId());
        assertEquals(captor.getAllValues().get(0).getId(), runCaptor.getValue().getRequestMessageId());
        assertEquals(captor.getAllValues().get(1).getId(), runCaptor.getValue().getResponseMessageId());
        assertEquals(runId, conversation.getLastRunId());
        assertEquals("AI搜索重构计划", conversation.getTitle());
        ChatExecutionContext.clear();
    }

    /**
     * 发送 sendMessageRejectsNonOwnerConversation 处理的消息或请求。
     */
    @Test
    void sendMessageRejectsNonOwnerConversation() {
        Long runId = bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        ForbiddenException exception = assertThrows(
            ForbiddenException.class,
            () -> chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "Hi", false), 2001L)

        );
        assertEquals("You cannot access this conversation", exception.getMessage());
        ChatExecutionContext.clear();
    }

    /**
     * 队列拒绝时应直接终止，不再进入模型调用链路。
     */
    @Test
    void sendMessageStopsWhenRuntimeGuardRejectsConversation() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        org.mockito.Mockito.doThrow(new IllegalStateException("Conversation rejected: busy"))
            .when(chatRuntimeGuardService).ensureAccepted(1L);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "Hi", false), 1002L)
        );

        assertEquals("Conversation rejected: busy", exception.getMessage());
        org.mockito.Mockito.verifyNoInteractions(aiChatClient);
        ChatExecutionContext.clear();
    }

    /**
     * 会话被取消后应以 CANCELLED 状态落库，而不是继续完成或失败收口。
     */
    @Test
    void sendMessagePersistsCancelledAssistantMessageWhenRuntimeMarkedCancelled() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("Hi", false, List.of("Hi"))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conversationIntentService.route("Hi", false)).thenReturn(new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null));
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        AtomicInteger cancelChecks = new AtomicInteger();
        when(chatRuntimeGuardService.isCancelled(1L)).thenAnswer(invocation -> cancelChecks.incrementAndGet() > 1);
        doAnswer(invocation -> {
            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onDelta("partial");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), org.mockito.ArgumentMatchers.anyBoolean(), any());

        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "Hi", false), 1002L);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals(ChatMessageStatus.CANCELLED, captor.getAllValues().get(1).getStatus());
        assertEquals("partial", captor.getAllValues().get(1).getContent());
        ChatExecutionContext.clear();
    }

    /**
     * 后台线程收到中断时也必须按取消状态收口，避免 run 永久停留在 RUNNING。
     */
    @Test
    void sendMessagePersistsCancelledMessageWhenStreamInterrupted() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("Hi", false, List.of("Hi"))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conversationIntentService.route("Hi", false)).thenReturn(new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null));
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatRuntimeGuardService.isCancelled(1L)).thenReturn(true);
        doAnswer(invocation -> {
            throw new IllegalStateException("interrupted");
        }).when(aiChatClient).streamChat(any(), org.mockito.ArgumentMatchers.anyBoolean(), any());

        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "Hi", false), 1002L);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals(ChatMessageStatus.CANCELLED, captor.getAllValues().get(1).getStatus());
        ChatExecutionContext.clear();
    }

    /**
     * 消息携带附件时应先校验并绑定到用户消息，确保前端回放可见。
     */
    @Test
    void sendMessageBindsAttachmentsToUserMessage() {
        Long runId = bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, ChatConversationStatus.ACTIVE);
        ChatAttachment attachment = ChatAttachment.builder()
            .id(5001L)
            .uploadedBy(1002L)
            .attachmentType("image")
            .fileName("demo.png")
            .mimeType("image/png")
            .fileSize(100L)
            .storageKey("chat/attachments/demo.png")
            .status("UPLOADED")
            .build();
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of(attachment));
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("Hi", false, List.of("Hi"))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conversationIntentService.route("Hi", false)).thenReturn(new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null));
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onDelta("ok");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), org.mockito.ArgumentMatchers.anyBoolean(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, "Hi", false, List.of(), List.of(), null, List.of(5001L)),
            1002L
        );

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        Long userMessageId = messageCaptor.getAllValues().get(0).getId();
        verify(chatAttachmentService).bindToMessage(attachment, 1L, userMessageId, runId);
        ChatExecutionContext.clear();
    }
}
