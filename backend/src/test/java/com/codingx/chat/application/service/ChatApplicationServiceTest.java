package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.automation.application.service.AutomationTaskChatCreationResult;
import com.codingx.automation.application.service.AutomationTaskChatCreationService;
import com.codingx.automation.domain.model.AutomationScheduleType;
import com.codingx.automation.domain.model.AutomationTask;
import com.codingx.automation.domain.model.AutomationTaskSourceType;
import com.codingx.chat.application.service.goal.ChatGoalView;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatExecutionRun;
import com.codingx.chat.domain.model.ChatExecutionStep;
import com.codingx.chat.domain.model.ChatIntentNode;
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
import com.codingx.common.support.ai.AiToolCall;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.exception.ConflictException;
import java.util.Map;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.expert.application.service.ChatExpertContextService;
import com.codingx.governance.application.service.GovernanceAgentContextService;
import com.codingx.governance.application.service.HookRuleService;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.skill.application.service.ChatSkillContextService;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.tool.application.service.ChatToolExecutionService;
import com.codingx.tool.application.service.ChatToolExecutionResult;
import com.codingx.tool.application.service.ChatToolSpecService;
import com.codingx.tool.application.service.ChatToolSpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

    @Mock
    private HookRuleService hookRuleService;

    @Mock
    private GovernanceAgentContextService governanceAgentContextService;

    /**
     * 自动化会话创建服务，用于在聊天中直接创建定时任务并返回助手摘要。
     */
    @Mock
    private AutomationTaskChatCreationService automationTaskChatCreationService;

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
     * 明确的自动化创建请求应直接在当前会话创建任务，并以助手消息返回创建结果。
     */
    @Test
    void sendMessageCreatesAutomationTaskAndRepliesInsideConversation() {
        Long runId = bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        AutomationTask task = AutomationTask.builder()
            .id(7001L)
            .userId(1002L)
            .workspaceId(3001L)
            .sourceType(AutomationTaskSourceType.CHAT)
            .sourceConversationId(1L)
            .name("每日项目总结")
            .prompt("总结项目状态")
            .scheduleType(AutomationScheduleType.DAILY)
            .scheduleTime("18:11")
            .enabled(true)
            .deleted(false)
            .build();
        String assistantContent = "已创建自动化任务：每日项目总结\n执行时间：每天 18:11\n需求：总结项目状态";
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(automationTaskChatCreationService.tryCreateFromChatMessage(conversation, "每天 18:11 帮我总结项目状态", 1002L, runId))
            .thenReturn(java.util.Optional.of(new AutomationTaskChatCreationResult(task, assistantContent)));
        when(conversationTitleService.generateTitle(org.mockito.ArgumentMatchers.eq(conversation), any())).thenReturn("每日项目总结");

        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "每天 18:11 帮我总结项目状态", false), 1002L);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        ChatMessage assistantMessage = captor.getAllValues().get(1);
        assertEquals(ChatMessageRole.ASSISTANT, assistantMessage.getRole());
        assertEquals(ChatMessageStatus.COMPLETED, assistantMessage.getStatus());
        assertTrue(assistantMessage.getContent().contains("已创建自动化任务"));
        assertTrue(assistantMessage.getContent().contains("18:11"));
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), eq(assistantMessage.getId()), eq(assistantContent), eq("每日项目总结"));
        verifyNoInteractions(conversationRewriteService, aiChatClient);
        ChatExecutionContext.clear();
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
            new ConversationRewriteResult("普通聊天问题", false, List.of("普通聊天问题"))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationIntentService.route("普通聊天问题", false)).thenReturn(new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null));
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
        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "普通聊天问题", false), 1002L);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        ArgumentCaptor<ChatExecutionRun> runCaptor = ArgumentCaptor.forClass(ChatExecutionRun.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        verify(chatExecutionRunRepository).save(runCaptor.capture());
        verify(chatRuntimeGuardService).ensureAccepted(1L);
        verify(conversationTitleService).generateTitle(org.mockito.ArgumentMatchers.eq(conversation), any());
        verify(conversationSummaryService).buildModelHistory(org.mockito.ArgumentMatchers.eq(1L), any());
        verify(conversationSummaryService).refreshSummaryIfNeeded(org.mockito.ArgumentMatchers.eq(conversation), any());
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("Hello world"), eq("AI搜索重构计划"));
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
            new ConversationRewriteResult("普通聊天问题", false, List.of("普通聊天问题"))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conversationIntentService.route("普通聊天问题", false)).thenReturn(new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null));
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

        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "普通聊天问题", false), 1002L);

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
            new ConversationRewriteResult("普通聊天问题", false, List.of("普通聊天问题"))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conversationIntentService.route("普通聊天问题", false)).thenReturn(new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null));
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatRuntimeGuardService.isCancelled(eq(1L), any(Long.class))).thenReturn(true);
        doAnswer(invocation -> {
            throw new IllegalStateException("interrupted");
        }).when(aiChatClient).streamChat(any(), org.mockito.ArgumentMatchers.anyBoolean(), any());

        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "普通聊天问题", false), 1002L);

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
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("技能已生效"), eq("技能对话"));
        ChatExecutionContext.clear();
    }

    /**
     * 仓库规范文件和已生效长期记忆应作为治理上下文注入系统提示，并在完成后提取新的长期记忆。
     */
    @Test
    void sendMessageInjectsGovernanceContextAndExtractsMemoryCandidatesAfterCompletion() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Governance", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("请记住我的代码风格偏好：优先写清楚业务注释", false, List.of("请记住我的代码风格偏好：优先写清楚业务注释"))
        );
        when(conversationIntentService.route("请记住我的代码风格偏好：优先写清楚业务注释", false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(1002L, 3001L, "请记住我的代码风格偏好：优先写清楚业务注释"))
            .thenReturn("# 仓库规范文件\n## AGENTS.md\n提交信息必须使用中文\n\n# 长期记忆\n代码风格偏好：业务注释");
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("长期记忆");
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<ChatMessage> aiHistory = invocation.getArgument(0, List.class);
            ChatMessage systemMessage = aiHistory.getFirst();
            assertEquals(ChatMessageRole.SYSTEM, systemMessage.getRole());
            assertTrue(systemMessage.getContent().contains("仓库规范文件"));
            assertTrue(systemMessage.getContent().contains("长期记忆"));
            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onDelta("已生成长期记忆。");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), eq(false), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, "请记住我的代码风格偏好：优先写清楚业务注释", false),
            1002L
        );

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        verify(governanceAgentContextService).buildAgentContext(
            1002L,
            3001L,
            "请记住我的代码风格偏好：优先写清楚业务注释"
        );
        verify(governanceAgentContextService).extractMemoryCandidates(
            eq(conversation),
            eq(messageCaptor.getAllValues().get(0)),
            eq(messageCaptor.getAllValues().get(1))
        );
        ChatExecutionContext.clear();
    }

    /**
     * 普通模型回复前的治理上下文应在意图路由期间提前预加载，避免每轮回答都串行等待上下文读取。
     */
    @Test
    void sendMessageShouldPreloadGovernanceContextBeforeIntentRouteCompletes() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Governance", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        CountDownLatch governanceContextStarted = new CountDownLatch(1);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("请帮我写一个 Java 工具类", false, List.of("请帮我写一个 Java 工具类"))
        );
        when(governanceAgentContextService.buildAgentContextAsync(1002L, 3001L, "请帮我写一个 Java 工具类"))
            .thenAnswer(invocation -> {
                governanceContextStarted.countDown();
                return CompletableFuture.completedFuture("# 仓库规范文件\n## AGENTS.md\n提交信息必须使用中文");
            });
        when(conversationIntentService.route("请帮我写一个 Java 工具类", false)).thenAnswer(invocation -> {
            assertTrue(governanceContextStarted.await(1, TimeUnit.SECONDS), "治理上下文应在意图路由完成前启动预加载");
            return new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null);
        });
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("治理预加载");
        doAnswer(invocation -> {
            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onDelta("已完成");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), eq(false), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, "请帮我写一个 Java 工具类", false),
            1002L
        );

        verify(governanceAgentContextService).buildAgentContextAsync(1002L, 3001L, "请帮我写一个 Java 工具类");
        ChatExecutionContext.clear();
    }

    /**
     * 桌面目标模式复用 planMode 时，系统提示必须明确目标工具调用约束。
     */
    @Test
    void planModeShouldInjectDesktopGoalToolGuidance() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String planModePrompt = new PromptTemplateLoader(Path.of("src/main/resources/prompt")).load("plan-mode-goal-context");
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn(planModePrompt);
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("请创建目标并跟进桌面目标模式改造", false, List.of("请创建目标并跟进桌面目标模式改造"))
        );
        when(conversationIntentService.route("请创建目标并跟进桌面目标模式改造", false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<ChatMessage> aiHistory = invocation.getArgument(0, List.class);
            String systemPrompt = aiHistory.getFirst().getContent();
            assertTrue(systemPrompt.contains("桌面端"));
            assertTrue(systemPrompt.contains("get_goal"));
            assertTrue(systemPrompt.contains("create_goal"));
            assertTrue(systemPrompt.contains("update_goal"));
            assertTrue(systemPrompt.contains("大型"));
            assertTrue(systemPrompt.contains("明确要求"));
            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onDelta("已进入目标模式");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), eq(false), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(
                1L,
                "请创建目标并跟进桌面目标模式改造",
                false,
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                List.of(),
                false,
                false,
                true
            ),
            1002L
        );

        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("已进入目标模式"), eq("目标模式"));
        verify(promptTemplateLoader).load("plan-mode-goal-context");
        ChatExecutionContext.clear();
    }

    /**
     * 目标模式必须绕过天气等意图澄清短路，避免用户明确要求创建目标时被提前回复“请明确城市”。
     */
    @Test
    void planModeShouldBypassClarifyShortCircuitAndReachModelTools() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        String planModePrompt = new PromptTemplateLoader(Path.of("src/main/resources/prompt")).load("plan-mode-goal-context");
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn(planModePrompt);
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult(question, false, List.of(question))
        );
        when(conversationIntentService.route(question, true)).thenReturn(
            new ConversationIntentDecision("weather-data", ConversationIntentAction.CLARIFY, "请明确你想查询哪个城市的天气，例如：上海今天天气怎么样。")
        );
        when(chatIntentNodeRepository.findByIntentCode("weather-data")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(5);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE")))
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any())).thenReturn(
            new ChatToolExecutionResult("update_goal", "目标已阻塞", Map.of("goal", Map.of(
                "id", "goal-1",
                "title", "做一个大型笔记html并完成提交",
                "status", "BLOCKED",
                "progressSummary", "目标已创建，等待实际执行工具"
            )))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<ChatMessage> aiHistory = invocation.getArgument(0, List.class);
            assertTrue(aiHistory.getFirst().getContent().contains("get_goal"));
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
            } else if (round == 2) {
                handler.onToolCall(new AiToolCall("call-2", "create_goal", "{\"title\":\"大型笔记 HTML\"}"));
            } else if (round == 3) {
                handler.onToolCall(new AiToolCall("call-3", "update_goal", "{\"goalKey\":\"default\",\"status\":\"BLOCKED\",\"progressSummary\":\"已进入目标工具链，等待后续执行工具\"}"));
            } else {
                handler.onDelta("目标工具链已进入。");
            }
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of("weather_query"), List.of(), Map.of(), null, null, List.of(), false, false, true),
            1002L
        );

        verify(chatToolExecutionService).execute(eq("get_goal"), any());
        verify(chatToolExecutionService).execute(eq("create_goal"), any());
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("目标工具链已进入。"), eq("目标模式"));
        ChatExecutionContext.clear();
    }

    /**
     * 目标模式中完成真实执行工具后，不能用正文“目标进度更新”冒充进度；必须继续调用 update_goal 写入数据库。
     */
    @Test
    void planModeShouldRequireUpdateGoalToolAfterWorkToolCompletes() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn("# 规划/目标模式\n必须调用 update_goal 写入真实目标进度。");
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult(question, false, List.of(question))
        );
        when(conversationIntentService.route(question, false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("write", "写入文件", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(6);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE")))
        );
        when(chatToolExecutionService.execute(eq("write"), any())).thenReturn(
            new ChatToolExecutionResult("write", "文件已写入", Map.of("path", "notes.html"))
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any())).thenReturn(
            new ChatToolExecutionResult("update_goal", "目标已完成", Map.of("goal", Map.of("id", "goal-1", "status", "COMPLETED")))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
            } else if (round == 2) {
                handler.onToolCall(new AiToolCall("call-2", "create_goal", "{\"title\":\"大型笔记 HTML\",\"steps\":[{\"title\":\"写入 HTML\"}]}"));
            } else if (round == 3) {
                handler.onToolCall(new AiToolCall("call-3", "write", "{\"path\":\"notes.html\",\"content\":\"<html></html>\"}"));
            } else if (round == 4) {
                handler.onDelta("目标进度更新：已完成大型笔记 HTML 初稿。");
            } else if (round == 5) {
                handler.onToolCall(new AiToolCall("call-4", "update_goal", "{\"goalKey\":\"default\",\"status\":\"COMPLETED\",\"progressSummary\":\"已完成大型笔记 HTML 初稿\",\"steps\":[{\"title\":\"写入 HTML\",\"status\":\"COMPLETED\"}]}"));
            } else {
                handler.onDelta("已写入大型笔记 HTML，并已同步真实目标进度。");
            }
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of(), List.of(), Map.of(), null, "D:\\code\\test", List.of(), false, false, true),
            1002L
        );

        verify(chatToolExecutionService).execute(eq("update_goal"), any());
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("已写入大型笔记 HTML，并已同步真实目标进度。"), eq("目标模式"));
        ChatExecutionContext.clear();
    }

    /**
     * 目标模式中关键工作工具完成后，下一轮必须先 update_goal；模型请求其他工具时应先被纠偏，避免一路执行却不更新浮窗。
     */
    @Test
    void planModeShouldBlockMoreToolsUntilGoalProgressUpdated() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn("# 规划/目标模式\n必须调用 update_goal 写入真实目标进度。");
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult(question, false, List.of(question))
        );
        when(conversationIntentService.route(question, false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("bash", "执行命令", Map.of()),
            new ChatToolSpec("ls", "列目录", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(6);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE")))
        );
        when(chatToolExecutionService.execute(eq("bash"), any())).thenReturn(
            new ChatToolExecutionResult("bash", "exitCode: 0\nstdout:\n目录已创建", Map.of("exitCode", 0))
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any())).thenReturn(
            new ChatToolExecutionResult("update_goal", "目标已完成", Map.of("goal", Map.of("id", "goal-1", "status", "COMPLETED")))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
            } else if (round == 2) {
                handler.onToolCall(new AiToolCall("call-2", "create_goal", "{\"title\":\"大型笔记 HTML\"}"));
            } else if (round == 3) {
                handler.onToolCall(new AiToolCall("call-3", "bash", "{\"command\":\"New-Item -ItemType Directory -Force -Path docs/notes\"}"));
            } else if (round == 4) {
                handler.onToolCall(new AiToolCall("call-4", "ls", "{\"path\":\"docs/notes\"}"));
            } else if (round == 5) {
                handler.onToolCall(new AiToolCall("call-5", "update_goal", "{\"goalKey\":\"default\",\"status\":\"COMPLETED\",\"progressSummary\":\"目录已创建\"}"));
            } else {
                handler.onDelta("已创建目录，并已同步目标进度。");
            }
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of(), List.of(), Map.of(), null, "D:\\code\\test", List.of(), false, false, true),
            1002L
        );

        verify(chatToolExecutionService, never()).execute(eq("ls"), any());
        verify(chatToolExecutionService).execute(eq("update_goal"), any());
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("已创建目录，并已同步目标进度。"), eq("目标模式"));
        ChatExecutionContext.clear();
    }

    /**
     * 用户明确要求直接执行时，目标模式不能在 create_goal 后用半截正文收口；必须继续执行工具或写入阻塞进度。
     */
    @Test
    void planModeShouldContinueAfterGoalCreatedWhenUserRequiresExecution() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn("# 规划/目标模式\n必须创建目标后继续执行，并调用 update_goal 写入真实目标进度。");
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult(question, false, List.of(question))
        );
        when(conversationIntentService.route(question, false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("bash", "执行命令", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(6);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE")))
        );
        when(chatToolExecutionService.execute(eq("bash"), any())).thenReturn(
            new ChatToolExecutionResult("bash", "exitCode: 0\nstdout:\n目录已创建", Map.of("exitCode", 0))
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any())).thenReturn(
            new ChatToolExecutionResult("update_goal", "目标已完成", Map.of("goal", Map.of("id", "goal-1", "status", "COMPLETED")))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
            } else if (round == 2) {
                handler.onDelta("好的，当前没有活动目标，我先创建目标，然后直接开始执行。");
                handler.onToolCall(new AiToolCall("call-2", "create_goal", "{\"title\":\"大型笔记 HTML\"}"));
            } else if (round == 3) {
                handler.onDelta("##");
            } else if (round == 4) {
                handler.onToolCall(new AiToolCall("call-3", "bash", "{\"command\":\"New-Item -ItemType Directory -Force -Path docs/notes\"}"));
            } else if (round == 5) {
                handler.onToolCall(new AiToolCall("call-4", "update_goal", "{\"goalKey\":\"default\",\"status\":\"COMPLETED\",\"progressSummary\":\"目录已创建\"}"));
            } else {
                handler.onDelta("已创建目录，并已同步目标进度。");
            }
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of(), List.of(), Map.of(), null, "D:\\code\\test", List.of(), false, false, true),
            1002L
        );

        verify(chatToolExecutionService).execute(eq("bash"), any());
        verify(chatToolExecutionService).execute(eq("update_goal"), any());
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("已创建目录，并已同步目标进度。"), eq("目标模式"));
        ChatExecutionContext.clear();
    }

    /**
     * get_goal 明确返回空时，执行型目标必须先 create_goal，不能继续执行其他工具或用正文伪造目标。
     */
    @Test
    void planModeShouldCreateGoalAfterEmptyGoalLookupBeforeWorkOrAnswer() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn("# 规划/目标模式\n目标为空时必须 create_goal，禁止正文伪造目标。");
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult(question, false, List.of(question))
        );
        when(conversationIntentService.route(question, false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("ls", "列目录", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(8);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE")))
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any())).thenReturn(
            new ChatToolExecutionResult("update_goal", "目标已阻塞", Map.of("goal", Map.of(
                "id", "goal-1",
                "title", "做一个大型笔记html并完成提交",
                "status", "BLOCKED",
                "progressSummary", "目标已创建，等待实际执行工具"
            )))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
                handler.onToolCall(new AiToolCall("call-2", "ls", "{}"));
            } else if (round == 2) {
                handler.onDelta("已创建目标 goal_fake，并准备直接执行。");
            } else if (round == 3) {
                handler.onToolCall(new AiToolCall("call-3", "create_goal", "{\"title\":\"大型笔记 HTML\"}"));
            } else if (round == 4) {
                handler.onToolCall(new AiToolCall("call-4", "update_goal", "{\"goalKey\":\"default\",\"status\":\"BLOCKED\",\"progressSummary\":\"目标已创建，等待实际执行工具\"}"));
            } else {
                handler.onDelta("已创建真实目标，并写入阻塞进度。");
            }
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of(), List.of(), Map.of(), null, "D:\\code\\test", List.of(), false, false, true),
            1002L
        );

        verify(chatToolExecutionService, never()).execute(eq("ls"), any());
        verify(chatToolExecutionService).execute(eq("create_goal"), any());
        verify(chatToolExecutionService).execute(eq("update_goal"), any());
        verify(chatStreamPublisher, never()).publishAssistantCompleted(eq(1L), any(Long.class), eq("已创建目标 goal_fake，并准备直接执行。"), eq("目标模式"));
        verify(chatStreamPublisher, never()).publishAssistantCompleted(eq(1L), any(Long.class), eq("已创建真实目标，并写入阻塞进度。"), eq("目标模式"));
        verify(chatStreamPublisher).publishAssistantCompleted(
            eq(1L),
            any(Long.class),
            eq("目标已阻塞：做一个大型笔记html并完成提交。\n阻塞原因：目标已创建，等待实际执行工具"),
            eq("目标模式")
        );
        ChatExecutionContext.clear();
    }

    /**
     * 目标模式原文被意图层误判为 SEARCH 时，不能先执行系统搜索并关闭本地目标工具。
     * 真实桌面失败链路是：搜索步骤完成后模型在正文里声称已创建目标，但 chat_goal 没有任何真实记录。
     */
    @Test
    void planModeShouldBypassSearchBranchAndUseGoalToolsWhenSearchIntentWins() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn("# 规划/目标模式\n搜索误判不能绕过目标工具。");
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult(question, true, List.of("创建目标：做一个大型笔记HTML并完成提交"))
        );
        when(conversationIntentService.route("创建目标：做一个大型笔记HTML并完成提交", false)).thenReturn(
            new ConversationIntentDecision("search-general", ConversationIntentAction.SEARCH, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("search-general")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(5);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE")))
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any())).thenReturn(
            new ChatToolExecutionResult("update_goal", "目标已阻塞", Map.of("goal", Map.of(
                "id", "goal-1",
                "title", "做一个大型笔记html并完成提交",
                "status", "BLOCKED",
                "progressSummary", "目标已创建，等待实际执行工具"
            )))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
            } else if (round == 2) {
                handler.onToolCall(new AiToolCall("call-2", "create_goal", "{\"title\":\"大型笔记 HTML\"}"));
            } else if (round == 3) {
                handler.onToolCall(new AiToolCall("call-3", "update_goal", "{\"status\":\"BLOCKED\",\"progressSummary\":\"目标已创建，等待实际执行工具\"}"));
            } else {
                handler.onDelta("已创建真实目标，并写入阻塞进度。");
            }
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of(), List.of(), Map.of(), null, "D:\\code\\test", List.of(), false, false, true),
            1002L
        );

        verify(webSearchExecutionService, never()).search(any());
        verify(searchReferenceCollector, never()).collect(any(), any(), any(), any());
        verify(documentArtifactService, never()).createDocxArtifact(any(), any(), any(), any());
        verify(chatToolExecutionService).execute(eq("get_goal"), any());
        verify(chatToolExecutionService).execute(eq("create_goal"), any());
        verify(chatToolExecutionService).execute(eq("update_goal"), any());
        verify(chatStreamPublisher, never()).publishAssistantCompleted(eq(1L), any(Long.class), eq("已创建真实目标，并写入阻塞进度。"), eq("目标模式"));
        verify(chatStreamPublisher).publishAssistantCompleted(
            eq(1L),
            any(Long.class),
            eq("目标已阻塞：做一个大型笔记html并完成提交。\n阻塞原因：目标已创建，等待实际执行工具"),
            eq("目标模式")
        );
        ChatExecutionContext.clear();
    }

    /**
     * 目标创建后若模型明确写入终态 update_goal，应允许其收口，避免没有可执行前置步骤时陷入轮次上限。
     * 关键约束：只有 COMPLETED/BLOCKED/CANCELLED 这类终态更新可越过执行工具过滤，ACTIVE 进度仍要等真实工作工具后再写。
     */
    @Test
    void planModeShouldAllowTerminalGoalUpdateWhenExecutionToolIsVisible() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn("# 规划/目标模式\n终态 update_goal 可用于真实阻塞收口。");
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult(question, false, List.of(question))
        );
        when(conversationIntentService.route(question, false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("ls", "列目录", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(6);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE")))
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any())).thenReturn(
            new ChatToolExecutionResult("update_goal", "目标已阻塞", Map.of("goal", Map.of(
                "id", "goal-1",
                "title", "做一个大型笔记html并完成提交",
                "status", "BLOCKED",
                "progressSummary", "缺少可执行写文件工具，目标已阻塞"
            )))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
            } else if (round == 2) {
                handler.onToolCall(new AiToolCall("call-2", "create_goal", "{\"title\":\"大型笔记 HTML\"}"));
            } else if (round == 3) {
                handler.onToolCall(new AiToolCall("call-3", "update_goal", "{\"goalKey\":\"default\",\"status\":\"BLOCKED\",\"progressSummary\":\"缺少可执行写文件工具，目标已阻塞\"}"));
            } else {
                handler.onDelta("已创建真实目标，并写入阻塞进度。");
            }
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of(), List.of(), Map.of(), null, "D:\\code\\test", List.of(), false, false, true),
            1002L
        );

        verify(chatToolExecutionService, never()).execute(eq("ls"), any());
        verify(chatToolExecutionService).execute(eq("update_goal"), any());
        verify(chatStreamPublisher, never()).publishAssistantCompleted(eq(1L), any(Long.class), eq("已创建真实目标，并写入阻塞进度。"), eq("目标模式"));
        verify(chatStreamPublisher).publishAssistantCompleted(
            eq(1L),
            any(Long.class),
            eq("目标已阻塞：做一个大型笔记html并完成提交。\n阻塞原因：缺少可执行写文件工具，目标已阻塞"),
            eq("目标模式")
        );
        ChatExecutionContext.clear();
    }

    /**
     * 目标已创建后应隐藏 get_goal/create_goal；关键工作完成但未更新进度时，只暴露 update_goal，避免模型反复查询目标耗尽轮次。
     */
    @Test
    void planModeShouldNarrowVisibleToolsAfterGoalBecomesAvailable() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn("# 规划/目标模式\n必须创建目标后继续执行，并调用 update_goal 写入真实目标进度。");
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult(question, false, List.of(question))
        );
        when(conversationIntentService.route(question, false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("bash", "执行命令", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(6);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE")))
        );
        when(chatToolExecutionService.execute(eq("bash"), any())).thenReturn(
            new ChatToolExecutionResult("bash", "exitCode: 0\nstdout:\n目录已创建", Map.of("exitCode", 0))
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any())).thenReturn(
            new ChatToolExecutionResult("update_goal", "目标已完成", Map.of("goal", Map.of("id", "goal-1", "status", "COMPLETED")))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        AtomicInteger modelRound = new AtomicInteger();
        List<List<String>> roundVisibleToolNames = new ArrayList<>();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<ChatToolSpec> visibleSpecs = invocation.getArgument(2, List.class);
            roundVisibleToolNames.add(visibleSpecs.stream().map(ChatToolSpec::name).toList());
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
            } else if (round == 2) {
                handler.onToolCall(new AiToolCall("call-2", "create_goal", "{\"title\":\"大型笔记 HTML\"}"));
            } else if (round == 3) {
                handler.onToolCall(new AiToolCall("call-3", "bash", "{\"command\":\"New-Item -ItemType Directory -Force -Path docs/notes\"}"));
            } else if (round == 4) {
                handler.onToolCall(new AiToolCall("call-4", "update_goal", "{\"goalKey\":\"default\",\"status\":\"COMPLETED\",\"progressSummary\":\"目录已创建\"}"));
            } else {
                handler.onDelta("已创建目录，并已同步目标进度。");
            }
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of(), List.of(), Map.of(), null, "D:\\code\\test", List.of(), false, false, true),
            1002L
        );

        assertEquals(List.of("bash"), roundVisibleToolNames.get(2));
        assertEquals(List.of("update_goal"), roundVisibleToolNames.get(3));
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("已创建目录，并已同步目标进度。"), eq("目标模式"));
        ChatExecutionContext.clear();
    }

    /**
     * ACTIVE 中间进度写入后，下一轮不能继续暴露 update_goal 让模型空转刷进度；必须先执行工作工具。
     */
    @Test
    void planModeShouldHideUpdateGoalAfterActiveProgressUntilMoreWorkCompletes() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn("# 规划/目标模式\nACTIVE 后必须继续执行工作工具。");
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult(question, false, List.of(question))
        );
        when(conversationIntentService.route(question, false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("ls", "列目录", Map.of()),
            new ChatToolSpec("write", "写文件", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(10);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE")))
        );
        when(chatToolExecutionService.execute(eq("ls"), any())).thenReturn(
            new ChatToolExecutionResult("ls", "note.html", Map.of("count", 1))
        );
        when(chatToolExecutionService.execute(eq("write"), any())).thenReturn(
            new ChatToolExecutionResult("write", "large-note.html 已写入", Map.of("path", "large-note.html"))
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any()))
            .thenReturn(new ChatToolExecutionResult("update_goal", "目标已更新", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE"))))
            .thenReturn(new ChatToolExecutionResult("update_goal", "目标已完成", Map.of("goal", Map.of("id", "goal-1", "status", "COMPLETED"))));
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        AtomicInteger modelRound = new AtomicInteger();
        List<List<String>> roundVisibleToolNames = new ArrayList<>();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<ChatToolSpec> visibleSpecs = invocation.getArgument(2, List.class);
            roundVisibleToolNames.add(visibleSpecs.stream().map(ChatToolSpec::name).toList());
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
            } else if (round == 2) {
                handler.onToolCall(new AiToolCall("call-2", "create_goal", "{\"title\":\"大型笔记 HTML\"}"));
            } else if (round == 3) {
                handler.onToolCall(new AiToolCall("call-3", "ls", "{}"));
            } else if (round == 4) {
                handler.onToolCall(new AiToolCall("call-4", "update_goal", "{\"status\":\"ACTIVE\",\"progressSummary\":\"已完成目录探查\"}"));
            } else if (round == 5) {
                handler.onToolCall(new AiToolCall("call-5", "update_goal", "{\"status\":\"ACTIVE\",\"progressSummary\":\"重复刷新进度\"}"));
                handler.onToolCall(new AiToolCall("call-6", "write", "{\"path\":\"large-note.html\",\"content\":\"<html></html>\"}"));
            } else if (round == 6) {
                handler.onToolCall(new AiToolCall("call-7", "update_goal", "{\"status\":\"COMPLETED\",\"progressSummary\":\"已完成\"}"));
            } else {
                handler.onDelta("已完成大型笔记 HTML，并已完成目标收口。");
            }
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of(), List.of(), Map.of(), null, "D:\\code\\test", List.of(), false, false, true),
            1002L
        );

        assertEquals(List.of("ls", "write"), roundVisibleToolNames.get(4));
        verify(chatToolExecutionService, times(1)).execute(eq("write"), any());
        verify(chatToolExecutionService, times(2)).execute(eq("update_goal"), any());
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("已完成大型笔记 HTML，并已完成目标收口。"), eq("目标模式"));
        ChatExecutionContext.clear();
    }

    /**
     * 目标模式中工作工具失败时，应把失败作为工具证据回灌给模型，而不是直接终止整轮目标执行。
     */
    @Test
    void planModeShouldRecoverFromWorkToolBusinessFailureAndUpdateGoal() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn("# 规划/目标模式\n工具失败时必须 update_goal 写入阻塞或调整后的进度。");
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult(question, false, List.of(question))
        );
        when(conversationIntentService.route(question, false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("edit", "编辑文件", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(6);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE")))
        );
        when(chatToolExecutionService.execute(eq("edit"), any())).thenThrow(
            new BusinessException("CHAT_TOOL_EDIT_TEXT_NOT_FOUND", "未找到要替换的文本")
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any())).thenReturn(
            new ChatToolExecutionResult("update_goal", "目标已阻塞", Map.of("goal", Map.of("id", "goal-1", "status", "BLOCKED")))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
            } else if (round == 2) {
                handler.onToolCall(new AiToolCall("call-2", "create_goal", "{\"title\":\"大型笔记 HTML\"}"));
            } else if (round == 3) {
                handler.onToolCall(new AiToolCall("call-3", "edit", "{\"path\":\"note.html\",\"oldText\":\"missing\",\"newText\":\"content\"}"));
            } else if (round == 4) {
                handler.onToolCall(new AiToolCall("call-4", "update_goal", "{\"goalKey\":\"default\",\"status\":\"BLOCKED\",\"progressSummary\":\"编辑失败，已记录阻塞原因\"}"));
            } else {
                handler.onDelta("编辑失败已写入目标进度，请调整替换文本后继续。");
            }
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of(), List.of(), Map.of(), null, "D:\\code\\test", List.of(), false, false, true),
            1002L
        );

        verify(chatToolExecutionService).execute(eq("edit"), any());
        verify(chatToolExecutionService).execute(eq("update_goal"), any());
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("编辑失败已写入目标进度，请调整替换文本后继续。"), eq("目标模式"));
        ChatExecutionContext.clear();
    }

    /**
     * 目标创建后的只读调研也是目标执行进展，不能用 `</think>` 或空正文直接收口，必须先 update_goal。
     */
    @Test
    void planModeShouldUpdateGoalAfterReadOnlyExecutionBeforeFinalAnswer() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn("# 规划/目标模式\n调研完成后也必须 update_goal 写入真实目标进度。");
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult(question, false, List.of(question))
        );
        when(conversationIntentService.route(question, false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("ls", "列目录", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(6);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE")))
        );
        when(chatToolExecutionService.execute(eq("ls"), any())).thenReturn(
            new ChatToolExecutionResult("ls", "note.html\nnotes.html", Map.of("count", 2))
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any())).thenReturn(
            new ChatToolExecutionResult("update_goal", "目标已完成", Map.of("goal", Map.of("id", "goal-1", "status", "COMPLETED")))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
            } else if (round == 2) {
                handler.onToolCall(new AiToolCall("call-2", "create_goal", "{\"title\":\"大型笔记 HTML\"}"));
            } else if (round == 3) {
                handler.onToolCall(new AiToolCall("call-3", "ls", "{}"));
            } else if (round == 4) {
                handler.onDelta("</think>");
            } else if (round == 5) {
                handler.onToolCall(new AiToolCall("call-4", "update_goal", "{\"goalKey\":\"default\",\"status\":\"COMPLETED\",\"progressSummary\":\"已完成目录调研\"}"));
            } else {
                handler.onDelta("已完成目录调研，并已同步目标进度。");
            }
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of(), List.of(), Map.of(), null, "D:\\code\\test", List.of(), false, false, true),
            1002L
        );

        verify(chatToolExecutionService).execute(eq("ls"), any());
        verify(chatToolExecutionService).execute(eq("update_goal"), any());
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("已完成目录调研，并已同步目标进度。"), eq("目标模式"));
        ChatExecutionContext.clear();
    }

    /**
     * 执行型目标的中间进度不能被当成最终交付；只有 update_goal 把目标写成终态后才能收口。
     */
    @Test
    void planModeShouldContinueAfterActiveGoalProgressUpdateUntilGoalTerminal() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn("# 规划/目标模式\n中间进度更新后必须继续执行，直到目标完成或阻塞。");
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult(question, false, List.of(question))
        );
        when(conversationIntentService.route(question, false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("ls", "列目录", Map.of()),
            new ChatToolSpec("write", "写文件", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(10);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE")))
        );
        when(chatToolExecutionService.execute(eq("ls"), any())).thenReturn(
            new ChatToolExecutionResult("ls", "note.html\nnotes.html", Map.of("count", 2))
        );
        when(chatToolExecutionService.execute(eq("write"), any())).thenReturn(
            new ChatToolExecutionResult("write", "note-pro.html 已写入", Map.of("path", "note-pro.html"))
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any()))
            .thenReturn(new ChatToolExecutionResult("update_goal", "目标已更新", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE"))))
            .thenReturn(new ChatToolExecutionResult("update_goal", "目标已完成", Map.of("goal", Map.of("id", "goal-1", "status", "COMPLETED"))));
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
            } else if (round == 2) {
                handler.onToolCall(new AiToolCall("call-2", "create_goal", "{\"title\":\"大型笔记 HTML\"}"));
            } else if (round == 3) {
                handler.onToolCall(new AiToolCall("call-3", "ls", "{}"));
            } else if (round == 4) {
                handler.onToolCall(new AiToolCall("call-4", "update_goal", "{\"goalKey\":\"default\",\"progressSummary\":\"完成目录调研\",\"status\":\"ACTIVE\"}"));
            } else if (round == 5) {
                handler.onDelta("已完成调研，下一步创建 note-pro.html。");
            } else if (round == 6) {
                handler.onToolCall(new AiToolCall("call-5", "write", "{\"path\":\"note-pro.html\",\"content\":\"<html></html>\"}"));
            } else if (round == 7) {
                handler.onToolCall(new AiToolCall("call-6", "update_goal", "{\"goalKey\":\"default\",\"status\":\"COMPLETED\",\"progressSummary\":\"已创建大型笔记 HTML 并完成提交\"}"));
            } else {
                handler.onDelta("已创建大型笔记 HTML，并已完成目标进度收口。");
            }
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of(), List.of(), Map.of(), null, "D:\\code\\test", List.of(), false, false, true),
            1002L
        );

        verify(chatToolExecutionService).execute(eq("write"), any());
        verify(chatToolExecutionService, times(2)).execute(eq("update_goal"), any());
        verify(chatStreamPublisher, never()).publishAssistantCompleted(eq(1L), any(Long.class), eq("已完成调研，下一步创建 note-pro.html。"), eq("目标模式"));
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("已创建大型笔记 HTML，并已完成目标进度收口。"), eq("目标模式"));
        ChatExecutionContext.clear();
    }

    /**
     * 桌面端真实链路中，模型可能在 ACTIVE 进度后直接输出“下一步/正在执行”的长正文且不再发工具。
     * 只要目标未进入终态，后端必须隐藏这类正文并继续要求工具执行，不能把中间说明当作完成结果落库。
     */
    @Test
    void planModeShouldSuppressNaturalLanguageAfterActiveGoalUntilTerminalUpdate() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn("# 规划/目标模式\nACTIVE 中间进度不能作为最终答复。");
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult(question, false, List.of(question))
        );
        when(conversationIntentService.route(question, false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("ls", "列目录", Map.of()),
            new ChatToolSpec("write", "写文件", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(10);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE")))
        );
        when(chatToolExecutionService.execute(eq("ls"), any())).thenReturn(
            new ChatToolExecutionResult("ls", "note.html", Map.of("count", 1))
        );
        when(chatToolExecutionService.execute(eq("write"), any())).thenReturn(
            new ChatToolExecutionResult("write", "note.html 已写入", Map.of("path", "note.html"))
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any()))
            .thenReturn(new ChatToolExecutionResult("update_goal", "目标已更新", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE"))))
            .thenReturn(new ChatToolExecutionResult("update_goal", "目标已完成", Map.of("goal", Map.of("id", "goal-1", "status", "COMPLETED"))));
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
            } else if (round == 2) {
                handler.onToolCall(new AiToolCall("call-2", "create_goal", "{\"title\":\"大型笔记 HTML\"}"));
            } else if (round == 3) {
                handler.onToolCall(new AiToolCall("call-3", "ls", "{}"));
            } else if (round == 4) {
                handler.onToolCall(new AiToolCall("call-4", "update_goal", "{\"status\":\"ACTIVE\",\"progressSummary\":\"已完成初始探查\"}"));
            } else if (round == 5) {
                handler.onDelta("目标已创建（状态：ACTIVE）。下一步关键执行步骤：增强 note.html，集成 Markdown 解析与实时预览。正在执行 → 修改 note.html。");
            } else if (round == 6) {
                handler.onToolCall(new AiToolCall("call-5", "write", "{\"path\":\"note.html\",\"content\":\"<html></html>\"}"));
            } else if (round == 7) {
                handler.onToolCall(new AiToolCall("call-6", "update_goal", "{\"status\":\"COMPLETED\",\"progressSummary\":\"已完成并提交\"}"));
            } else {
                handler.onDelta("已完成大型笔记 HTML，并已通过 update_goal 写入完成状态。");
            }
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of(), List.of(), Map.of(), null, "D:\\code\\test", List.of(), false, false, true),
            1002L
        );

        verify(chatToolExecutionService).execute(eq("write"), any());
        verify(chatToolExecutionService, times(2)).execute(eq("update_goal"), any());
        verify(chatStreamPublisher, never()).publishAssistantCompleted(eq(1L), any(Long.class), eq("目标已创建（状态：ACTIVE）。下一步关键执行步骤：增强 note.html，集成 Markdown 解析与实时预览。正在执行 → 修改 note.html。"), eq("目标模式"));
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("已完成大型笔记 HTML，并已通过 update_goal 写入完成状态。"), eq("目标模式"));
        ChatExecutionContext.clear();
    }

    /**
     * 目标模式中同一路径重复 write 不能走普通快捷完成分支；目标仍为 ACTIVE 时必须继续让模型写入终态 update_goal。
     * 桌面端真实失败链路是：write 成功、update_goal 仍 ACTIVE、模型再次 write 同一路径，旧逻辑直接输出“已完成，文件已写入”。
     */
    @Test
    void planModeShouldNotFinishOnRepeatedWriteBeforeTerminalGoalUpdate() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn("# 规划/目标模式\n重复写入不能替代目标终态进度。");
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult(question, false, List.of(question))
        );
        when(conversationIntentService.route(question, false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("write", "写文件", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(10);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE")))
        );
        when(chatToolExecutionService.execute(eq("write"), any())).thenReturn(
            new ChatToolExecutionResult("write", "notebook.html 已写入", Map.of("path", "notebook.html"))
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any()))
            .thenReturn(new ChatToolExecutionResult("update_goal", "目标已更新", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE"))))
            .thenReturn(new ChatToolExecutionResult("update_goal", "目标已完成", Map.of("goal", Map.of("id", "goal-1", "status", "COMPLETED"))));
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
            } else if (round == 2) {
                handler.onToolCall(new AiToolCall("call-2", "create_goal", "{\"title\":\"大型笔记 HTML\"}"));
            } else if (round == 3) {
                handler.onToolCall(new AiToolCall("call-3", "write", "{\"path\":\"notebook.html\",\"content\":\"<html>第一版</html>\"}"));
            } else if (round == 4) {
                handler.onToolCall(new AiToolCall("call-4", "update_goal", "{\"status\":\"ACTIVE\",\"progressSummary\":\"已写入初稿\"}"));
            } else if (round == 5) {
                handler.onToolCall(new AiToolCall("call-5", "write", "{\"path\":\"notebook.html\",\"content\":\"<html>第二版</html>\"}"));
            } else if (round == 6) {
                handler.onToolCall(new AiToolCall("call-6", "update_goal", "{\"status\":\"COMPLETED\",\"progressSummary\":\"已完成并提交\"}"));
            } else {
                handler.onDelta("已完成大型笔记 HTML，并已通过 update_goal 写入完成状态。");
            }
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of(), List.of(), Map.of(), null, "D:\\code\\test", List.of(), false, false, true),
            1002L
        );

        verify(chatToolExecutionService, times(1)).execute(eq("write"), any());
        verify(chatToolExecutionService, times(2)).execute(eq("update_goal"), any());
        verify(chatStreamPublisher, never()).publishAssistantCompleted(eq(1L), any(Long.class), eq("已完成，文件已写入 `notebook.html`。"), eq("目标模式"));
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("已完成大型笔记 HTML，并已通过 update_goal 写入完成状态。"), eq("目标模式"));
        ChatExecutionContext.clear();
    }

    /**
     * 目标工具进入 BLOCKED 后，最终答复必须以数据库目标状态为准，不能让模型继续编造“已完成/已提交”。
     */
    @Test
    void planModeShouldUseTerminalGoalSnapshotInsteadOfModelClaimAfterBlockedUpdate() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        String blockedSummary = "环境缺少文件写入工具，无法创建 HTML 或完成 git 提交。";
        ChatGoalView blockedGoal = new ChatGoalView(
            "goal-1",
            "1",
            "default",
            "做一个大型笔记html并完成提交",
            "创建大型笔记 HTML 并提交",
            "BLOCKED",
            blockedSummary,
            "GOAL_BLOCKED",
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            List.of()
        );
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn("# 规划/目标模式\n终态目标以数据库为准。");
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult(question, false, List.of(question))
        );
        when(conversationIntentService.route(question, false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("bash", "执行命令", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(8);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE")))
        );
        when(chatToolExecutionService.execute(eq("bash"), any())).thenReturn(
            new ChatToolExecutionResult("bash", "exitCode: 1\nstderr:\nfatal: your current branch does not have any commits yet", Map.of("exitCode", 1))
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any())).thenReturn(
            new ChatToolExecutionResult("update_goal", "目标已更新", Map.of("goal", blockedGoal))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
            } else if (round == 2) {
                handler.onToolCall(new AiToolCall("call-2", "create_goal", "{\"title\":\"大型笔记 HTML\"}"));
            } else if (round == 3) {
                handler.onToolCall(new AiToolCall("call-3", "bash", "{\"command\":\"git status --short\"}"));
            } else if (round == 4) {
                handler.onToolCall(new AiToolCall("call-4", "update_goal", "{\"status\":\"BLOCKED\",\"progressSummary\":\"" + blockedSummary + "\"}"));
            } else {
                handler.onDelta("目标完成：COMPLETED。已提交 commit d9ceb9a。");
            }
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of(), List.of(), Map.of(), null, "D:\\code\\test", List.of(), false, false, true),
            1002L
        );

        verify(chatStreamPublisher, never()).publishAssistantCompleted(eq(1L), any(Long.class), eq("目标完成：COMPLETED。已提交 commit d9ceb9a。"), eq("目标模式"));
        verify(chatStreamPublisher).publishAssistantCompleted(
            eq(1L),
            any(Long.class),
            eq("目标已阻塞：做一个大型笔记html并完成提交。\n阻塞原因：" + blockedSummary),
            eq("目标模式")
        );
        ChatExecutionContext.clear();
    }

    /**
     * update_goal 已返回 BLOCKED 的真实 ChatGoalView 后，工具循环必须立即以目标快照收口，不能继续执行后续工具。
     * 真实桌面失败链路是：BLOCKED 已落库，模型仍尝试 edit，随后轮次耗尽再补写 BLOCKED，因为 active goal 已不存在而报“目标不存在”。
     */
    @Test
    void planModeShouldStopToolLoopAfterBlockedChatGoalViewResult() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        String blockedSummary = "检查完成：large-notes.html 已存在；Git 仓库尚未有提交。缺少文件写入和 git 命令执行能力，无法继续执行。";
        ChatGoalView activeGoal = new ChatGoalView(
            "goal-1",
            "1",
            "default",
            "做一个大型笔记html并完成提交",
            "创建大型笔记 HTML 并提交",
            "ACTIVE",
            "已读取文件并开始检查仓库状态",
            "GOAL_UPDATED",
            LocalDateTime.now(),
            LocalDateTime.now(),
            null,
            List.of()
        );
        ChatGoalView blockedGoal = new ChatGoalView(
            "goal-1",
            "1",
            "default",
            "做一个大型笔记html并完成提交",
            "创建大型笔记 HTML 并提交",
            "BLOCKED",
            blockedSummary,
            "GOAL_BLOCKED",
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            List.of()
        );
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn("# 规划/目标模式\n终态目标后必须停止工具循环。");
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult(question, false, List.of(question))
        );
        when(conversationIntentService.route(question, false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("read", "读取文件", Map.of()),
            new ChatToolSpec("bash", "执行命令", Map.of()),
            new ChatToolSpec("edit", "编辑文件", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(10);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", activeGoal))
        );
        when(chatToolExecutionService.execute(eq("read"), any())).thenReturn(
            new ChatToolExecutionResult("read", "<!DOCTYPE html><html lang=\"zh-CN\"></html>", Map.of("path", "large-notes.html"))
        );
        when(chatToolExecutionService.execute(eq("bash"), any())).thenReturn(
            new ChatToolExecutionResult("bash", "exitCode: 1\nstderr:\nfatal: your current branch 'master' does not have any commits yet", Map.of("exitCode", 1))
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any()))
            .thenReturn(new ChatToolExecutionResult("update_goal", "目标已更新", Map.of("goal", activeGoal)))
            .thenReturn(new ChatToolExecutionResult("update_goal", "目标已更新", Map.of("goal", blockedGoal)))
            .thenThrow(new BusinessException("CHAT_GOAL_NOT_FOUND", "目标不存在"));
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
            } else if (round == 2) {
                handler.onToolCall(new AiToolCall("call-2", "create_goal", "{\"title\":\"做一个大型笔记html并完成提交\"}"));
            } else if (round == 3) {
                handler.onToolCall(new AiToolCall("call-3", "read", "{\"path\":\"large-notes.html\"}"));
            } else if (round == 4) {
                handler.onToolCall(new AiToolCall("call-4", "update_goal", "{\"status\":\"ACTIVE\",\"progressSummary\":\"已读取文件并开始检查仓库状态\"}"));
            } else if (round == 5) {
                handler.onToolCall(new AiToolCall("call-5", "bash", "{\"command\":\"git log --oneline -5\"}"));
            } else if (round == 6) {
                handler.onToolCall(new AiToolCall("call-6", "update_goal", "{\"status\":\"BLOCKED\",\"progressSummary\":\"" + blockedSummary + "\"}"));
            } else {
                handler.onToolCall(new AiToolCall("call-7", "edit", "{\"path\":\"large-notes.html\",\"old_text\":\"missing\",\"new_text\":\"new\"}"));
            }
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of(), List.of(), Map.of(), null, "D:\\code\\test", List.of(), false, false, true),
            1002L
        );

        verify(chatToolExecutionService, times(2)).execute(eq("update_goal"), any());
        verify(chatToolExecutionService, never()).execute(eq("edit"), any());
        verify(chatStreamPublisher).publishAssistantCompleted(
            eq(1L),
            any(Long.class),
            eq("目标已阻塞：做一个大型笔记html并完成提交。\n阻塞原因：" + blockedSummary),
            eq("目标模式")
        );
        ChatExecutionContext.clear();
    }

    /**
     * 目标已创建后如果 provider 在工具参数流中断，必须把目标写成 BLOCKED 并给出用户可见答复。
     * 桌面端真实失败链路是：write 参数预览已经显示 big-notes.html，但完整 tool_call 未结束，文件未落地且 run 最终 ERROR。
     */
    @Test
    void planModeShouldBlockGoalWhenToolStreamFailsAfterGoalCreated() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        String blockedSummary = "模型流式响应在首包后失败，文件写入未完成，后续验证和提交尚未执行。请重试或缩小单次写入内容。";
        Map<String, Object> blockedGoal = Map.of(
            "id", "goal-1",
            "title", "做一个大型笔记html并完成提交",
            "status", "BLOCKED",
            "progressSummary", blockedSummary
        );
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn("# 规划/目标模式\n模型流失败时必须 update_goal 写入 BLOCKED。");
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult(question, false, List.of(question))
        );
        when(conversationIntentService.route(question, false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("write", "写入文件", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(6);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE")))
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any())).thenReturn(
            new ChatToolExecutionResult("update_goal", "目标已更新", Map.of("goal", blockedGoal))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
                handler.onComplete();
                return null;
            }
            if (round == 2) {
                handler.onToolCall(new AiToolCall("call-2", "create_goal", "{\"title\":\"大型笔记 HTML\"}"));
                handler.onComplete();
                return null;
            }
            handler.onToolCallDelta(new com.codingx.common.support.ai.AiToolCallDelta(
                "call-3",
                "write",
                "{\"path\":\"big-notes.html\"",
                "{\"path\":\"big-notes.html\""
            ));
            throw new IllegalStateException(ErrorMessageCatalog.AI_STREAM_FAILED_AFTER_FIRST_TOKEN);
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of(), List.of(), Map.of(), null, "D:\\code\\test", List.of(), false, false, true),
            1002L
        );

        ArgumentCaptor<String> goalUpdateArgsCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatToolExecutionService).execute(eq("update_goal"), goalUpdateArgsCaptor.capture());
        assertTrue(goalUpdateArgsCaptor.getValue().contains("\"status\":\"BLOCKED\""));
        assertTrue(goalUpdateArgsCaptor.getValue().contains("文件写入未完成"));
        verify(chatToolExecutionService, never()).execute(eq("write"), any());
        verify(chatStreamPublisher).publishAssistantCompleted(
            eq(1L),
            any(Long.class),
            eq("目标已阻塞：做一个大型笔记html并完成提交。\n阻塞原因：" + blockedSummary),
            eq("目标模式")
        );
        ChatExecutionContext.clear();
    }

    /**
     * get_goal 已确认没有目标后，如果第二轮模型流通过 onError 失败，后端必须先补建真实目标再写 BLOCKED。
     * 真实桌面失败链路是：第一轮 get_goal 返回 exists=false，第二轮 provider 首包后超时，run 直接 ERROR 且右侧没有目标。
     */
    @Test
    void planModeShouldCreateAndBlockGoalWhenStreamFailsAfterEmptyGoalLookup() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        String blockedSummary = "模型流式响应在首包后失败，目标已创建但执行工具尚未开始；后续文件写入、验证和提交没有真实完成。请重试或缩小单次写入内容。";
        Map<String, Object> blockedGoal = Map.of(
            "id", "goal-1",
            "title", "做一个大型笔记html并完成提交",
            "status", "BLOCKED",
            "progressSummary", blockedSummary
        );
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn("# 规划/目标模式\n目标为空且模型流失败时必须创建真实目标并写入 BLOCKED。");
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult(question, false, List.of(question))
        );
        when(conversationIntentService.route(question, false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("write", "写入文件", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(6);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", Map.of("id", "goal-1", "title", "做一个大型笔记html并完成提交", "status", "ACTIVE")))
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any())).thenReturn(
            new ChatToolExecutionResult("update_goal", "目标已更新", Map.of("goal", blockedGoal))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
                handler.onComplete();
                return null;
            }
            handler.onDelta("我将创建目标并开始执行。");
            handler.onError(new IllegalStateException(ErrorMessageCatalog.AI_STREAM_FAILED_AFTER_FIRST_TOKEN));
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of(), List.of(), Map.of(), null, "D:\\code\\test", List.of(), false, false, true),
            1002L
        );

        ArgumentCaptor<String> createGoalArgsCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatToolExecutionService).execute(eq("create_goal"), createGoalArgsCaptor.capture());
        assertTrue(createGoalArgsCaptor.getValue().contains("做一个大型笔记html并完成提交"));
        ArgumentCaptor<String> goalUpdateArgsCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatToolExecutionService).execute(eq("update_goal"), goalUpdateArgsCaptor.capture());
        assertTrue(goalUpdateArgsCaptor.getValue().contains("\"status\":\"BLOCKED\""));
        assertTrue(goalUpdateArgsCaptor.getValue().contains("执行工具尚未开始"));
        verify(chatStreamPublisher).publishAssistantCompleted(
            eq(1L),
            any(Long.class),
            eq("目标已阻塞：做一个大型笔记html并完成提交。\n阻塞原因：" + blockedSummary),
            eq("目标模式")
        );
        ChatExecutionContext.clear();
    }

    /**
     * 目标模式中重复读取同一文件不能退回普通流式收口。
     * 真实桌面失败链路是：目标已创建并写入 ACTIVE 进度，模型重复 read 后被后端转普通回答，
     * 最终正文声称“目标状态：COMPLETED/已提交”，但数据库目标仍停留在 ACTIVE。
     */
    @Test
    void planModeShouldBlockRepeatedReadFinalAnswerWhenGoalStillActive() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        String falseCompletionContent = """
            ### 🎯 目标状态：**COMPLETED**
            ✅ 已模拟提交，提交准备就绪。文件 `note-large.html` 就绪，可立即使用或提交。
            """.stripIndent().trim();
        String blockedSummary = "目标执行已开始，但模型未把目标推进到完成、阻塞或取消状态；后续验证和提交没有真实完成，已阻塞以避免误报完成。";
        Map<String, Object> blockedGoal = Map.of(
            "id", "goal-1",
            "title", "做一个大型笔记html并完成提交",
            "status", "BLOCKED",
            "progressSummary", blockedSummary
        );
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn("# 规划/目标模式\n重复读文件不能绕过目标终态。");
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult(question, false, List.of(question))
        );
        when(conversationIntentService.route(question, false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("read", "读取文件", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(8);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE")))
        );
        when(chatToolExecutionService.execute(eq("read"), any())).thenReturn(
            new ChatToolExecutionResult("read", "<!DOCTYPE html><html lang=\"zh-CN\"></html>", Map.of("path", "notebook.html"))
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any()))
            .thenReturn(new ChatToolExecutionResult("update_goal", "目标已更新", Map.of("goal", Map.of("id", "goal-1", "title", "做一个大型笔记html并完成提交", "status", "ACTIVE", "progressSummary", "已读取候选笔记文件并确定基础模板"))))
            .thenReturn(new ChatToolExecutionResult("update_goal", "目标已阻塞", Map.of("goal", blockedGoal)));
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
            } else if (round == 2) {
                handler.onToolCall(new AiToolCall("call-2", "create_goal", "{\"title\":\"做一个大型笔记html并完成提交\"}"));
            } else if (round == 3 || round == 5) {
                handler.onToolCall(new AiToolCall("call-read-" + round, "read", "{\"path\":\"notebook.html\"}"));
            } else if (round == 4) {
                handler.onToolCall(new AiToolCall("call-4", "update_goal", "{\"status\":\"ACTIVE\",\"progressSummary\":\"已读取候选笔记文件并确定基础模板\"}"));
            } else {
                handler.onDelta(falseCompletionContent);
            }
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());
        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of(), List.of(), Map.of(), null, "D:\\code\\test", List.of(), false, false, true),
            1002L
        );

        ArgumentCaptor<String> goalUpdateArgsCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatToolExecutionService, times(2)).execute(eq("update_goal"), goalUpdateArgsCaptor.capture());
        assertTrue(goalUpdateArgsCaptor.getAllValues().get(1).contains("\"status\":\"BLOCKED\""));
        verify(chatStreamPublisher, never()).publishAssistantCompleted(eq(1L), any(Long.class), eq(falseCompletionContent), eq("目标模式"));
        verify(chatStreamPublisher).publishAssistantCompleted(
            eq(1L),
            any(Long.class),
            eq("目标已阻塞：做一个大型笔记html并完成提交。\n阻塞原因：" + blockedSummary),
            eq("目标模式")
        );
        ChatExecutionContext.clear();
    }

    /**
     * 桌面端如果只靠用户原文触发目标模式，后端也不能在目标仍为 ACTIVE 时保存模型的“已完成/已提交”正文。
     * 真实失败链路是：get_goal/create_goal/write/update_goal(ACTIVE) 已落库，模型随后输出交付摘要并声称验证、提交完成，但没有真实验证或 git 提交工具证据。
     */
    @Test
    void explicitGoalModePromptShouldBlockWhenGoalNeverReachesTerminalState() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Goal", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        String question = "开启目标模式。请先检查当前线程是否已有目标；如果没有，请创建一个目标。目标是：做一个大型笔记html并完成提交。执行过程中每完成一个关键步骤，都要更新目标进度。不要只给方案，请直接执行、验证、提交。";
        String blockedSummary = "目标执行已开始，但模型未把目标推进到完成、阻塞或取消状态；后续验证和提交没有真实完成，已阻塞以避免误报完成。";
        Map<String, Object> blockedGoal = Map.of(
            "id", "goal-1",
            "title", "做一个大型笔记html并完成提交",
            "status", "BLOCKED",
            "progressSummary", blockedSummary
        );
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("plan-mode-goal-context")).thenReturn("# 规划/目标模式\n目标未终态时禁止输出完成正文。");
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("检查当前线程是否已有目标；如果没有，则创建目标：做一个大型笔记html并完成提交", false, List.of("检查当前线程是否已有目标"))
        );
        when(conversationIntentService.route(any(), eq(false))).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("get_goal", "读取目标", Map.of()),
            new ChatToolSpec("create_goal", "创建目标", Map.of()),
            new ChatToolSpec("write", "写入文件", Map.of()),
            new ChatToolSpec("update_goal", "更新目标", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(6);
        when(chatToolExecutionService.execute(eq("get_goal"), any())).thenReturn(
            new ChatToolExecutionResult("get_goal", "当前会话没有活动目标，请根据用户要求调用 create_goal 创建目标。", Map.of("exists", false))
        );
        when(chatToolExecutionService.execute(eq("create_goal"), any())).thenReturn(
            new ChatToolExecutionResult("create_goal", "目标已创建", Map.of("goal", Map.of("id", "goal-1", "status", "ACTIVE")))
        );
        when(chatToolExecutionService.execute(eq("write"), any())).thenReturn(
            new ChatToolExecutionResult("write", "notebook.html 已写入", Map.of("path", "notebook.html"))
        );
        when(chatToolExecutionService.execute(eq("update_goal"), any()))
            .thenReturn(new ChatToolExecutionResult("update_goal", "目标已更新", Map.of("goal", Map.of("id", "goal-1", "title", "做一个大型笔记html并完成提交", "status", "ACTIVE", "progressSummary", "已完成基础 UI 框架搭建"))))
            .thenReturn(new ChatToolExecutionResult("update_goal", "目标已阻塞", Map.of("goal", blockedGoal)));
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("目标模式");
        String falseCompletionContent = """
            ### 🎯 目标状态：**COMPLETED**
            ✅ 已模拟提交，提交准备就绪。文件 `note-large.html` 就绪，可立即使用或提交。
            """.stripIndent().trim();
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            int round = modelRound.incrementAndGet();
            if (round == 1) {
                handler.onToolCall(new AiToolCall("call-1", "get_goal", "{}"));
            } else if (round == 2) {
                handler.onToolCall(new AiToolCall("call-2", "create_goal", "{\"title\":\"做一个大型笔记html并完成提交\"}"));
            } else if (round == 3) {
                handler.onToolCall(new AiToolCall("call-3", "write", "{\"path\":\"notebook.html\",\"content\":\"<html></html>\"}"));
            } else if (round == 4) {
                handler.onToolCall(new AiToolCall("call-4", "update_goal", "{\"status\":\"ACTIVE\",\"progressSummary\":\"已完成基础 UI 框架搭建\"}"));
            } else {
                handler.onDelta(falseCompletionContent);
            }
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, question, false, List.of(), List.of(), Map.of(), null, "D:\\code\\test", List.of(), false, false, false),
            1002L
        );

        ArgumentCaptor<String> goalUpdateArgsCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatToolExecutionService, times(2)).execute(eq("update_goal"), goalUpdateArgsCaptor.capture());
        assertTrue(goalUpdateArgsCaptor.getAllValues().get(1).contains("\"status\":\"BLOCKED\""));
        verify(chatStreamPublisher, never()).publishAssistantCompleted(eq(1L), any(Long.class), eq(falseCompletionContent), eq("目标模式"));
        verify(chatStreamPublisher).publishAssistantCompleted(
            eq(1L),
            any(Long.class),
            eq("目标已阻塞：做一个大型笔记html并完成提交。\n阻塞原因：" + blockedSummary),
            eq("目标模式")
        );
        ChatExecutionContext.clear();
    }

    /**
     * 开启深度思考时，系统提示必须明确要求思考过程和最终回答都使用中文，避免前端直接展示英文 reasoning。
     */
    @Test
    void deepThinkingShouldInjectChineseReasoningGuidance() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Thinking", 1002L, ChatConversationStatus.ACTIVE);
        String deepThinkingPrompt = new PromptTemplateLoader(Path.of("src/main/resources/prompt"))
            .load("deep-thinking-language-guidance");
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.load("deep-thinking-language-guidance")).thenReturn(deepThinkingPrompt);
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("请帮我分析一下这个方案", false, List.of("请帮我分析一下这个方案"))
        );
        when(conversationIntentService.route("请帮我分析一下这个方案", false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(governanceAgentContextService.buildAgentContext(any(), any(), any())).thenReturn("");
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("中文思考");
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<ChatMessage> aiHistory = invocation.getArgument(0, List.class);
            String systemPrompt = aiHistory.getFirst().getContent();
            assertTrue(systemPrompt.contains("中文"), "深度思考模式应显式约束中文输出");
            assertTrue(systemPrompt.contains("思考过程"), "深度思考模式应显式约束 thinking 使用中文");
            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onThinkingDelta("先分析需求");
            handler.onDelta("结论如下");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), eq(true), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, "请帮我分析一下这个方案", true),
            1002L
        );

        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("结论如下"), eq("中文思考"));
        verify(promptTemplateLoader).load("deep-thinking-language-guidance");
        ChatExecutionContext.clear();
    }

    /**
     * 深度思考语言约束属于可维护 Prompt 资产，必须放在 resources/prompt 下，避免业务文案散落在编排代码里。
     */
    @Test
    void deepThinkingGuidanceShouldBeStoredAsPromptResource() throws IOException {
        Path promptResource = Path.of("src/main/resources/prompt/deep-thinking-language-guidance.st");
        assertTrue(Files.exists(promptResource));
        String promptContent = new PromptTemplateLoader(promptResource.getParent()).load("deep-thinking-language-guidance");
        assertTrue(promptContent.contains("深度思考语言约束"));
        assertTrue(promptContent.contains("简体中文"));

        String serviceSource = Files.readString(
            Path.of("src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java"),
            StandardCharsets.UTF_8
        );
        assertFalse(serviceSource.contains("即使模型内部默认使用英文推理"));
    }

    /**
     * 搜索证据、本地工具证据与工具执行证据都属于可维护 Prompt 资产，不应继续内联在编排服务里。
     */
    @Test
    void evidencePromptsShouldBeStoredAsPromptResources() throws IOException {
        Path promptDir = Path.of("src/main/resources/prompt");
        PromptTemplateLoader loader = new PromptTemplateLoader(promptDir);
        assertTrue(loader.load("search-evidence-context").contains("# 联网检索证据"));
        assertTrue(loader.load("tool-evidence-context").contains("# 工具执行证据"));
        assertTrue(loader.load("local-tool-evidence-context").contains("# 本地工具执行结果"));

        String serviceSource = Files.readString(
            Path.of("src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java"),
            StandardCharsets.UTF_8
        );
        assertFalse(serviceSource.contains("你只能依据下方检索证据回答"));
        assertFalse(serviceSource.contains("你必须基于本次工具结果整理最终回答"));
        assertFalse(serviceSource.contains("路径约束：后续读写文件必须基于当前真实工作目录"));
    }

    /**
     * 目标模式提示词属于可维护 Prompt 资产，必须放在 resources/prompt 下，避免业务提示散落在编排代码里。
     */
    @Test
    void planModePromptShouldBeStoredAsPromptResource() throws IOException {
        // 步骤 1：先检查资源文件本身存在，确保运维或产品同学可以独立维护目标模式提示词。
        Path promptResource = Path.of("src/main/resources/prompt/plan-mode-goal-context.st");
        assertTrue(Files.exists(promptResource));
        String promptContent = new PromptTemplateLoader(promptResource.getParent()).load("plan-mode-goal-context");
        assertTrue(promptContent.contains("# 规划/目标模式"));
        assertTrue(promptContent.contains("create_goal"));

        // 步骤 2：再约束应用服务只负责加载资源，不继续内联保存整段中文提示词。
        String serviceSource = Files.readString(
            Path.of("src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java"),
            StandardCharsets.UTF_8
        );
        assertFalse(serviceSource.contains("当前请求来自 CLI 或桌面端的规划/目标模式。请先判断用户"));
    }

    /**
     * 选中技能时，数据库消息正文必须保留 @skill 标记，但模型历史仍使用剥离后的自然语言正文。
     */
    @Test
    void sendMessagePersistsSkillMentionButUsesPlainQuestionForAiHistory() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), eq("请阅读 https://example.com"))).thenReturn(
            new ConversationRewriteResult("请阅读 https://example.com", false, List.of("请阅读 https://example.com"))
        );
        when(conversationIntentService.route("请阅读 https://example.com", false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(List.of("web-access"))).thenReturn("web-access skill context");
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("技能对话");
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<ChatMessage> aiHistory = invocation.getArgument(0, List.class);
            ChatMessage userMessageInAiHistory = aiHistory.stream()
                .filter(message -> message.getRole() == ChatMessageRole.USER)
                .findFirst()
                .orElseThrow();
            assertEquals("请阅读 https://example.com", userMessageInAiHistory.getContent());
            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onDelta("已按网页技能处理");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), eq(false), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, "请阅读 https://example.com", false, List.of(), List.of("web-access")),
            1002L
        );

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        assertEquals("@web-access 请阅读 https://example.com", messageCaptor.getAllValues().getFirst().getContent());
        ChatExecutionContext.clear();
    }

    /**
     * 选中技能后用户只问“这是啥”时，应解释当前引用的技能，而不是误判为缺少执行目标。
     * 业务约束：解释技能本身不需要 URL 或页面；只有执行技能任务时才需要追问目标。
     */
    @Test
    void sendMessageRoutesSelectedSkillShortQuestionThroughModelInsteadOfStaticIntro() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), eq("这是啥"))).thenReturn(
            new ConversationRewriteResult("这是啥", false, List.of("这是啥"))
        );
        when(conversationIntentService.route("这是啥", false)).thenReturn(
            new ConversationIntentDecision("sys-about-bot", ConversationIntentAction.DIRECT, null)
        );
        org.mockito.Mockito.lenient().when(chatIntentNodeRepository.findByIntentCode("sys-about-bot")).thenReturn(
            ChatIntentNode.builder()
                .intentCode("sys-about-bot")
                .intentType("system")
                .promptTemplate("系统介绍 prompt：请介绍 CodingX 助手能力。")
                .build()
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(List.of("web-access"))).thenReturn("web-access skill context");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("技能对话");
        org.mockito.Mockito.lenient().when(chatToolSpecService.listModelVisibleToolSpecs()).thenThrow(
            new AssertionError("技能短句说明不应查询或暴露本地工具，否则前端会等工具模式缓冲结束后才看到正文")
        );
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<ChatMessage> aiHistory = invocation.getArgument(0, List.class);
            assertTrue(aiHistory.getFirst().getContent().contains("web-access skill context"));
            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onDelta("web-access 是联网访问技能，可以搜索、抓取网页或操作浏览器。");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), org.mockito.ArgumentMatchers.anyBoolean(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, "这是啥 @web-access", false, List.of(), List.of("web-access")),
            1002L
        );

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        ArgumentCaptor<ChatExecutionRun> runCaptor = ArgumentCaptor.forClass(ChatExecutionRun.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        verify(chatExecutionRunRepository).save(runCaptor.capture());
        assertEquals("@web-access 这是啥", messageCaptor.getAllValues().getFirst().getContent());
        assertEquals("web-access 是联网访问技能，可以搜索、抓取网页或操作浏览器。", messageCaptor.getAllValues().get(1).getContent());
        assertEquals("chat.normal", runCaptor.getValue().getIntentCode());
        verify(chatStreamPublisher).publishUserMessage(1L, "这是啥");
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("web-access 是联网访问技能，可以搜索、抓取网页或操作浏览器。"), eq("技能对话"));
        verify(conversationRewriteService).rewriteResult(any(), eq("这是啥"));
        verify(chatSkillContextService).buildSkillContext(List.of("web-access"));
        verify(conversationSummaryService).buildModelHistory(any(), any());
        verify(aiChatClient, never()).streamChatWithTools(any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), any());
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
        when(runtimeSettingService.webSearchEnabled()).thenReturn(true);
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
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("截至当前检索，Java 24 已发布"), eq("联网搜索结果"));
        ChatExecutionContext.clear();
    }

    /**
     * 工具回灌后的模型续写可能重复第一轮已流式输出的开场白，后端必须在发布前跳过重复前缀。
     */
    @Test
    void sendMessageSkipsRepeatedPublishedPrefixAfterToolCall() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, String> slots = invocation.getArgument(1, Map.class);
            return "工具标识：" + slots.get("tool_code") + "\n工具输出：" + slots.get("tool_output");
        });
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("帮我创建 HTML 游戏", false, List.of("帮我创建 HTML 游戏"))
        );
        when(conversationIntentService.route("帮我创建 HTML 游戏", false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("shell_command", "执行命令", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(2);
        when(chatToolExecutionService.execute(eq("shell_command"), any())).thenReturn(
            new ChatToolExecutionResult("shell_command", "文件已写入", Map.of("exitCode", 0))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("HTML 游戏");
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            if (modelRound.incrementAndGet() == 1) {
                handler.onDelta("我将为你创建 HTML 游戏。");
                handler.onToolCall(new AiToolCall("call-1", "shell_command", "{\"command\":\"write index.html\"}"));
                handler.onComplete();
                return null;
            }
            handler.onDelta("我将为你创建 HTML 游戏。");
            handler.onDelta("\n\n已创建完成。");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "帮我创建 HTML 游戏", false), 1002L);

        ArgumentCaptor<String> deltaCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatStreamPublisher, org.mockito.Mockito.times(2)).publishAssistantDelta(eq(1L), deltaCaptor.capture());
        assertEquals(List.of("我将为你创建 HTML 游戏。", "\n\n已创建完成。"), deltaCaptor.getAllValues());
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        assertEquals("我将为你创建 HTML 游戏。\n\n已创建完成。", messageCaptor.getAllValues().get(1).getContent());
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("我将为你创建 HTML 游戏。\n\n已创建完成。"), eq("HTML 游戏"));
        ChatExecutionContext.clear();
    }

    /**
     * update_plan 工具结果中的步骤必须落库为 plan 类型步骤，并通过 step 事件推送给前端。
     */
    @Test
    void sendMessagePersistsUpdatePlanStepsAsExecutionSteps() {
        bindRunContext();
        ChatConversation conversation = ChatConversation.create(1L, "Default", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("先制定计划", false, List.of("先制定计划"))
        );
        when(conversationIntentService.route("先制定计划", false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("update_plan", "更新计划", Map.of())
        ));
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(2);
        when(chatToolExecutionService.execute(eq("update_plan"), any())).thenReturn(
            new ChatToolExecutionResult(
                "update_plan",
                "计划已更新，共 3 个步骤",
                Map.of(
                    "planId",
                    "default",
                    "steps",
                    List.of(
                        Map.of("step", "梳理需求", "status", "completed"),
                        Map.of("step", "实现治理接口", "status", "in_progress"),
                        Map.of("step", "验证前端交互", "status", "pending")
                    )
                )
            )
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("计划模式");
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            if (modelRound.incrementAndGet() == 1) {
                handler.onToolCall(new AiToolCall("call-1", "update_plan", "{\"steps\":[]}"));
                handler.onComplete();
                return null;
            }
            handler.onDelta("计划已同步。");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, "先制定计划", false, List.of(), List.of(), null, null, null, List.of(), false, false, true),
            1002L
        );

        ArgumentCaptor<ChatExecutionStep> stepCaptor = ArgumentCaptor.forClass(ChatExecutionStep.class);
        verify(chatExecutionStepRepository, org.mockito.Mockito.atLeast(4)).save(stepCaptor.capture());
        List<ChatExecutionStep> planSteps = stepCaptor.getAllValues().stream()
            .filter(step -> "plan".equals(step.getStepType()))
            .toList();
        assertEquals(3, planSteps.size());
        assertEquals("梳理需求", planSteps.get(0).getStepTitle());
        assertEquals("COMPLETED", planSteps.get(0).getStepStatus());
        assertEquals("实现治理接口", planSteps.get(1).getStepTitle());
        assertEquals("RUNNING", planSteps.get(1).getStepStatus());
        assertEquals("验证前端交互", planSteps.get(2).getStepTitle());
        assertEquals("PENDING", planSteps.get(2).getStepStatus());
        verify(chatStreamPublisher, org.mockito.Mockito.atLeast(3)).publishStep(eq(1L), any());
        ChatExecutionContext.clear();
    }

    /**
     * 重新生成应从上一轮 run 的隐藏上下文步骤恢复专家，并在新 run 上重新保存同一上下文。
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
        when(chatExecutionStepRepository.findByRunId(9001L)).thenReturn(List.of(
            ChatExecutionStep.builder()
                .id(7001L)
                .runId(9001L)
                .stepType("runtime_context")
                .metadataJson("{\"skillCodes\":[\"web-read\"],\"mcpCodes\":[\"weather_query\"],\"expertCode\":\"solution-architect\"}")
                .build()
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

        ArgumentCaptor<ChatExecutionStep> contextStepCaptor = ArgumentCaptor.forClass(ChatExecutionStep.class);
        verify(chatExecutionStepRepository, org.mockito.Mockito.atLeastOnce()).save(contextStepCaptor.capture());
        assertTrue(
            contextStepCaptor.getAllValues().stream().anyMatch(step ->
                ChatRunContextStepSupport.STEP_TYPE.equals(step.getStepType())
                    && step.getMetadataJson().contains("web-read")
                    && step.getMetadataJson().contains("weather_query")
                    && step.getMetadataJson().contains("solution-architect")
            )
        );
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("新答案"), eq("新答案"));
        ChatExecutionContext.clear();
    }
}

