package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatIntentNode;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.chat.domain.repository.ChatIntentNodeRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.chat.domain.service.AiChatClient;
import com.codingx.chat.domain.service.ChatStreamPublisher;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
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
    private com.codingx.support.ai.TokenCounterService tokenCounterService;

    @Mock
    private com.codingx.support.ai.LlmResponseCleaner llmResponseCleaner;

    @Mock
    private ChatMcpRepository chatMcpRepository;

    @InjectMocks
    private ChatApplicationService chatApplicationService;

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
        verify(chatStreamPublisher).publishAssistantCompleted(1L, "请补充你指的是哪一部分", "New Conversation");
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
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("你是谁", false, java.util.List.of("你是谁"))
        );
        when(conversationIntentService.route("你是谁", false)).thenReturn(
            new ConversationIntentDecision("sys-about-bot", ConversationIntentAction.DIRECT, null)
        );
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
        verify(chatStreamPublisher).publishAssistantCompleted(1L, "我是配置驱动的知识助手。", "关于助手");
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals("我是配置驱动的知识助手。", captor.getAllValues().get(1).getContent());
        ChatExecutionContext.clear();
    }
}
