package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

