package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatExecutionRun;
import com.codingx.chat.domain.model.ChatIntentNode;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.chat.domain.repository.ChatExecutionStepRepository;
import com.codingx.chat.domain.repository.ChatIntentNodeRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.chat.domain.port.AiChatClient;
import com.codingx.chat.domain.port.ChatStreamPublisher;
import com.codingx.expert.application.service.ChatExpertContextService;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.skill.application.service.ChatSkillContextService;
import java.util.ArrayList;
import java.util.List;
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
    private ChatExecutionStepRepository chatExecutionStepRepository;

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

    @Mock
    private PromptTemplateLoader promptTemplateLoader;

    @Mock
    private ChatIntentNodeRepository chatIntentNodeRepository;

    @Mock
    private com.codingx.common.support.ai.TokenCounterService tokenCounterService;

    @Mock
    private com.codingx.common.support.ai.LlmResponseCleaner llmResponseCleaner;

    @Mock
    private ChatMcpRepository chatMcpRepository;

    @Mock
    private ChatAttachmentService chatAttachmentService;

    @Mock
    private ChatSkillContextService chatSkillContextService;

    @Mock
    private ChatExpertContextService chatExpertContextService;

    @InjectMocks
    private ChatApplicationService chatApplicationService;

    /**
     * 纯问候属于确定性轻量回复，不能再进入改写、意图识别和模型工具调用链路，避免简单输入等待数秒。
     */
    @Test
    void sendMessageRepliesGreetingWithoutRewriteIntentOrModelCall() {
        Long runId = 9101000L;
        ChatExecutionContext.start(runId);
        ChatConversation conversation = ChatConversation.create(1L, "New Conversation", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);

        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "你好", false), 1002L);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals("你好", captor.getAllValues().get(0).getContent());
        assertEquals(ChatMessageStatus.COMPLETED, captor.getAllValues().get(1).getStatus());
        assertEquals("你好，我在。你可以直接说要查资料、改代码、看项目，或让我帮你梳理问题。", captor.getAllValues().get(1).getContent());
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("你好，我在。你可以直接说要查资料、改代码、看项目，或让我帮你梳理问题。"), eq("New Conversation"));
        ArgumentCaptor<ChatExecutionRun> runCaptor = ArgumentCaptor.forClass(ChatExecutionRun.class);
        verify(chatExecutionRunRepository).save(runCaptor.capture());
        assertEquals(runId, runCaptor.getValue().getId());
        assertEquals("sys-welcome", runCaptor.getValue().getIntentCode());
        assertEquals(ChatMessageStatus.COMPLETED.name(), runCaptor.getValue().getStatus());
        verify(chatMessageRepository, never()).findByConversationId(any());
        verify(chatAttachmentService, never()).requireOwnedAttachments(any(), any(), any());
        verify(chatExecutionStepRepository, never()).findByRunId(any());
        verify(conversationRewriteService, never()).rewriteResult(any(), any());
        verify(conversationIntentService, never()).route(any(), org.mockito.Mockito.anyBoolean());
        org.mockito.Mockito.verifyNoInteractions(aiChatClient);
        ChatExecutionContext.clear();
    }

    /**
     * 客户端可能保留上一次选择的 MCP；纯问候仍应优先走确定性快答，避免被无关工具选择拖入改写和模型链路。
     */
    @Test
    void sendMessageRepliesGreetingWithoutRewriteIntentOrModelCallWhenMcpSelected() {
        Long runId = 9101003L;
        ChatExecutionContext.start(runId);
        ChatConversation conversation = ChatConversation.create(1L, "New Conversation", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);

        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "你好", false, List.of("weather_query")), 1002L);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals("你好", captor.getAllValues().get(0).getContent());
        assertEquals(ChatMessageStatus.COMPLETED, captor.getAllValues().get(1).getStatus());
        assertEquals("你好，我在。你可以直接说要查资料、改代码、看项目，或让我帮你梳理问题。", captor.getAllValues().get(1).getContent());
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("你好，我在。你可以直接说要查资料、改代码、看项目，或让我帮你梳理问题。"), eq("New Conversation"));
        ArgumentCaptor<ChatExecutionRun> runCaptor = ArgumentCaptor.forClass(ChatExecutionRun.class);
        verify(chatExecutionRunRepository).save(runCaptor.capture());
        assertEquals(runId, runCaptor.getValue().getId());
        assertEquals("sys-welcome", runCaptor.getValue().getIntentCode());
        assertEquals(ChatMessageStatus.COMPLETED.name(), runCaptor.getValue().getStatus());
        verify(chatMessageRepository, never()).findByConversationId(any());
        verify(chatAttachmentService, never()).requireOwnedAttachments(any(), any(), any());
        verify(chatExecutionStepRepository, never()).findByRunId(any());
        verify(conversationRewriteService, never()).rewriteResult(any(), any());
        verify(conversationIntentService, never()).route(any(), org.mockito.Mockito.anyBoolean());
        org.mockito.Mockito.verifyNoInteractions(aiChatClient);
        ChatExecutionContext.clear();
    }

    /**
     * 歧义问题应直接返回澄清消息，并跳过模型调用。
     */
    @Test
    void sendMessageReturnsClarificationWhenIntentIsAmbiguous() {
        Long runId = 9101001L;
        ChatExecutionContext.start(runId);
        ChatConversation conversation = ChatConversation.create(1L, "New Conversation", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("这个要怎么改", false, java.util.List.of("这个要怎么改"))
        );
        when(conversationIntentService.route("这个要怎么改", false)).thenReturn(
            new ConversationIntentDecision("clarify.ambiguity", ConversationIntentAction.CLARIFY, "请补充你指的是哪一部分")
        );

        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "这个要怎么改", false), 1002L);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals(ChatMessageStatus.COMPLETED, captor.getAllValues().get(1).getStatus());
        assertEquals("请补充你指的是哪一部分", captor.getAllValues().get(1).getContent());
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("请补充你指的是哪一部分"), eq("New Conversation"));
        org.mockito.Mockito.verifyNoInteractions(aiChatClient);
        ChatExecutionContext.clear();
    }

    /**
     * SYSTEM 意图命中后应使用节点 Prompt 进入模型回答，而不是走 Java 硬编码文案。
     */
    @Test
    void sendMessageStreamsSystemReplyFromNodePrompt() {
        Long runId = 9101002L;
        ChatExecutionContext.start(runId);
        ChatConversation conversation = ChatConversation.create(1L, "New Conversation", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("你是谁", false, java.util.List.of("你是谁"))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conversationIntentService.route("你是谁", false)).thenReturn(
            new ConversationIntentDecision("sys-about-bot", ConversationIntentAction.DIRECT, null)
        );
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatIntentNodeRepository.findByIntentCode("sys-about-bot")).thenReturn(
            ChatIntentNode.builder()
                .intentCode("sys-about-bot")
                .intentType("system")
                .promptTemplate("你是企业内部知识助手，请用一句简短中文介绍自己。")
                .build()
        );
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("关于助手");
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<ChatMessage> history = invocation.getArgument(0, List.class);
            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            assertEquals(ChatMessageRole.SYSTEM, history.getFirst().getRole());
            assertEquals("你是企业内部知识助手，请用一句简短中文介绍自己。", history.getFirst().getContent());
            handler.onDelta("我是配置驱动的知识助手。");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), eq(false), any());

        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "你是谁", false), 1002L);

        verify(aiChatClient).streamChat(any(), eq(false), any());
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("我是配置驱动的知识助手。"), eq("关于助手"));
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals("我是配置驱动的知识助手。", captor.getAllValues().get(1).getContent());
        ChatExecutionContext.clear();
    }
}

