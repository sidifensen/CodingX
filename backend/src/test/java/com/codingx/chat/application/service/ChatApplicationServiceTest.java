package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.codingx.chat.domain.model.ChatTraceRun;
import com.codingx.chat.domain.model.ChatAttachment;
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
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.ConflictException;
import java.util.Map;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.expert.application.service.ChatExpertContextService;
import com.codingx.expert.domain.model.ChatExpert;
import com.codingx.expert.domain.repository.ChatExpertRepository;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.skill.application.service.ChatSkillContextService;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.tool.application.service.ChatToolExecutionService;
import com.codingx.tool.application.service.ChatToolSpecService;
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

    @Mock
    private ChatExecutionStepRepository chatExecutionStepRepository;

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
    private ChatSkillRepository chatSkillRepository;

    @Mock
    private ChatExpertRepository chatExpertRepository;

    @Mock
    private ChatAttachmentService chatAttachmentService;

    @Mock
    private ChatSkillContextService chatSkillContextService;

    @Mock
    private ChatExpertContextService chatExpertContextService;

    @Mock
    private WebSearchExecutionService webSearchExecutionService;

    @Mock
    private SearchReferenceCollector searchReferenceCollector;

    @Mock
    private DocumentArtifactService documentArtifactService;

    @Mock
    private ConversationTraceRecordService conversationTraceRecordService;

    @Mock
    private RuntimeSettingService runtimeSettingService;

    @Mock
    private ChatToolSpecService chatToolSpecService;

    @Mock
    private ChatToolExecutionService chatToolExecutionService;

    @Mock
    private ChatWorkspaceBindingService chatWorkspaceBindingService;

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
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        doAnswer(invocation -> {

            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onThinkingDelta("thinking");
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
        assertEquals("thinking", captor.getAllValues().get(1).getThinkingContent());
        assertTrue(captor.getAllValues().get(1).getThinkingDuration() != null);
        assertTrue(captor.getAllValues().get(1).getThinkingDuration() > 0);
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
        assertEquals(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN, exception.getMessage());
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
        org.mockito.Mockito.doThrow(new ConflictException(ErrorMessageCatalog.CHAT_QUEUE_BUSY))
            .when(chatRuntimeGuardService).ensureAccepted(1L);

        ConflictException exception = assertThrows(
            ConflictException.class,
            () -> chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "Hi", false), 1002L)
        );

        assertEquals(ErrorMessageCatalog.CHAT_QUEUE_BUSY, exception.getMessage());
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
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        AtomicInteger cancelChecks = new AtomicInteger();
        when(chatRuntimeGuardService.isCancelled(eq(1L), any(Long.class)))
            .thenAnswer(invocation -> cancelChecks.incrementAndGet() > 1);
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
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatRuntimeGuardService.isCancelled(eq(1L), any(Long.class))).thenReturn(true);
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
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onDelta("ok");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), org.mockito.ArgumentMatchers.anyBoolean(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, "Hi", false, List.of(), List.of(), null, null, List.of(5001L)),
            1002L
        );

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        Long userMessageId = messageCaptor.getAllValues().get(0).getId();
        verify(chatAttachmentService).bindToMessage(attachment, 1L, userMessageId, runId);
        ChatExecutionContext.clear();
    }

    /**
     * 选中技能时应把技能上下文注入系统消息，确保模型可消费真实技能说明。
     */
    @Test
    void sendMessageInjectsSkillContextIntoSystemPrompt() {
        Long runId = bindRunContext();
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
        when(chatSkillContextService.buildSkillContext(List.of("weather_query"))).thenReturn("""
            以下是当前消息已选择技能的说明文档，请优先按这些技能约束回答。
            ## /weather_query（天气查询）
            ---
            name: weather_query
            ---
            """);
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("技能对话");
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<ChatMessage> aiHistory = invocation.getArgument(0, List.class);
            ChatMessage systemMessage = aiHistory.getFirst();
            assertEquals(ChatMessageRole.SYSTEM, systemMessage.getRole());
            org.junit.jupiter.api.Assertions.assertTrue(
                systemMessage.getContent().contains("/weather_query"),
                "系统提示应包含技能上下文"
            );
            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onDelta("技能已生效");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), org.mockito.ArgumentMatchers.anyBoolean(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, "Hi", false, List.of(), List.of("weather_query"), null, null, List.of()),
            1002L
        );

        verify(chatSkillContextService).buildSkillContext(List.of("weather_query"));
        verify(chatStreamPublisher).publishAssistantCompleted(1L, "技能已生效", "技能对话");
        ChatExecutionContext.clear();
    }

    /**
     * 本地运行态的聊天历史由客户端快照负责，后端只做临时推理与 SSE 推送。
     * 关键约束：不得读取或写入 chat_conversation/chat_message，避免本地历史默认进入云端数据库。
     */
    @Test
    void sendMessageLocalOnlyStreamsWithoutPersistingConversationMessages() {
        Long runId = bindRunContext();
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("分析本地代码", false, List.of("分析本地代码"))
        );
        when(conversationIntentService.route("分析本地代码", false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onDelta("本地分析完成");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), org.mockito.ArgumentMatchers.anyBoolean(), any());

        chatApplicationService.sendMessage(
            SendChatMessageCommand.localOnly(9901L, "分析本地代码", false, List.of(), List.of(), Map.of(), null, "D:/code/test", List.of(), false),
            1002L
        );

        verify(chatRuntimeGuardService).ensureAccepted(9901L);
        verify(chatConversationRepository, never()).requireById(any());
        verify(chatConversationRepository, never()).save(any());
        verify(chatMessageRepository, never()).findByConversationId(any());
        verify(chatMessageRepository, never()).save(any());
        verify(chatExecutionRunRepository, never()).save(any());
        verify(chatStreamPublisher).publishUserMessage(9901L, "分析本地代码");
        verify(chatStreamPublisher).publishAssistantCompleted(9901L, "本地分析完成", "分析本地代码");
        assertEquals(runId, ChatExecutionContext.currentRunId().orElseThrow());
        ChatExecutionContext.clear();
    }

    /**
     * 本地运行态即使命中搜索类意图，也不能写入执行步骤、搜索引用或文档产物表。
     * 关键约束：本地搜索过程只通过 SSE 与本地快照表达，避免 task/run 侧表把本地历史带上云端。
     */
    @Test
    void sendMessageLocalOnlySearchIntentSkipsPersistentProcessRecords() {
        bindRunContext();
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("搜索本地依赖资料", false, List.of("搜索本地依赖资料"))
        );
        when(conversationIntentService.route("搜索本地依赖资料", false)).thenReturn(
            new ConversationIntentDecision("search-general", ConversationIntentAction.SEARCH, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("search-general")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onDelta("本地搜索回答");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), org.mockito.ArgumentMatchers.anyBoolean(), any());

        chatApplicationService.sendMessage(
            SendChatMessageCommand.localOnly(9902L, "搜索本地依赖资料", false, List.of(), List.of(), Map.of(), null, "D:/code/test", List.of(), false),
            1002L
        );

        verify(chatExecutionStepRepository, never()).save(any());
        verify(searchReferenceCollector, never()).collect(any(), any(), any(), any());
        verify(documentArtifactService, never()).createDocxArtifact(any(), any(), any(), any());
        verify(chatExecutionRunRepository, never()).save(any());
        verify(chatStreamPublisher).publishAssistantCompleted(9902L, "本地搜索回答", "搜索本地依赖资料");
        ChatExecutionContext.clear();
    }

    /**
     * 命中 SEARCH 意图时应把联网证据注入系统提示，避免模型仅依赖旧记忆回答。
     */
    @Test
    void sendMessageInjectsSearchEvidenceIntoSystemPrompt() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("请联网搜索最新 Java 版本", false, List.of("请联网搜索最新 Java 版本"))
        );
        when(conversationIntentService.route("请联网搜索最新 Java 版本", false)).thenReturn(
            new ConversationIntentDecision("search-general", ConversationIntentAction.SEARCH, null)
        );
        when(runtimeSettingService.searchMaxParallelQuestions()).thenReturn(1);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(searchExecutor).execute(any(Runnable.class));
        when(webSearchExecutionService.search("请联网搜索最新 Java 版本")).thenReturn(List.of(
            new SearchReferenceCandidate(
                "Java 24 发布说明",
                "https://example.com/java24",
                "Oracle",
                "Java 24 已正式发布，包含新特性更新",
                0.98D
            )
        ));
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("联网搜索结果");
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<ChatMessage> aiHistory = invocation.getArgument(0, List.class);
            ChatMessage systemMessage = aiHistory.getFirst();
            assertEquals(ChatMessageRole.SYSTEM, systemMessage.getRole());
            org.junit.jupiter.api.Assertions.assertTrue(
                systemMessage.getContent().contains("联网检索证据"),
                "系统提示应注入联网证据段落"
            );
            org.junit.jupiter.api.Assertions.assertTrue(
                systemMessage.getContent().contains("Java 24 发布说明"),
                "系统提示应包含搜索返回标题"
            );
            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onDelta("截至当前检索，Java 24 已发布");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), eq(false), any());

        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "请联网搜索最新 Java 版本", false), 1002L);

        verify(searchReferenceCollector).collect(eq(9001001L), any(Long.class), eq(1L), any());
        verify(documentArtifactService).createDocxArtifact(eq(9001001L), any(Long.class), eq(1L), eq("搜索结果整理中"));
        verify(chatStreamPublisher).publishAssistantCompleted(1L, "截至当前检索，Java 24 已发布", "联网搜索结果");
        ChatExecutionContext.clear();
    }

    /**
     * 重新生成应复用上一轮已完成 run 的上下文绑定，并在新 run 上重新落库绑定关系。
     */
    @Test
    void regenerateLastAssistantMessageReusesPreviousRunBindings() {
        ChatConversation conversation = ChatConversation.create(1L, "历史会话", 1002L, ChatConversationStatus.ACTIVE);
        conversation.restoreRuntimeState(null, 9001L);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(List.of(
            ChatMessage.create(11L, 1L, ChatMessageRole.USER, "第一条问题", ChatMessageStatus.COMPLETED, null, null, null).attachRun(9001L),
            ChatMessage.create(12L, 1L, ChatMessageRole.ASSISTANT, "旧答案", ChatMessageStatus.COMPLETED, null, null, null).attachRun(9001L)
        ));
        when(chatSkillRepository.findByTaskId(9001L)).thenReturn(List.of(
            ChatSkill.builder().skillCode("web-read").displayName("网页读取").build()
        ));
        when(chatMcpRepository.findByTaskId(9001L)).thenReturn(List.of(
            ChatMcp.builder().mcpCode("weather_query").displayName("天气查询").build()
        ));
        when(chatExpertRepository.findByTaskId(9001L)).thenReturn(List.of(
            ChatExpert.builder().expertCode("solution-architect").displayName("解决方案架构师").build()
        ));
        when(conversationTraceRecordService.startTrace(eq("chat-regenerate"), eq(1L), eq(1002L))).thenReturn(
            ChatTraceRun.builder().traceId("trace-1").conversationId(1L).taskId(9001L).userId(1002L).status("RUNNING").build()
        );
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("第一条问题", false, List.of("第一条问题"))
        );
        when(conversationIntentService.route("第一条问题", true)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("新答案");
        doAnswer(invocation -> {
            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onMetadata("mock-provider", "mock-model");
            handler.onDelta("新答案");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), eq(false), any());

        chatApplicationService.regenerateLastAssistantMessage(1L, 1002L);

        verify(chatSkillRepository).bindTaskSkills(any(Long.class), eq(List.of("web-read")));
        verify(chatMcpRepository).bindTaskMcps(any(Long.class), eq(List.of("weather_query")));
        verify(chatExpertRepository).bindTaskExpert(any(Long.class), eq("solution-architect"));
        verify(chatStreamPublisher).publishAssistantCompleted(1L, "新答案", "新答案");
        ChatExecutionContext.clear();
    }
}

