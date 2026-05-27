package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.domain.port.AiChatClient;
import com.codingx.chat.domain.port.ChatStreamPublisher;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.chat.domain.repository.ChatExecutionStepRepository;
import com.codingx.chat.domain.repository.ChatIntentNodeRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.common.support.ai.AiToolCall;
import com.codingx.common.exception.BusinessException;
import com.codingx.expert.application.service.ChatExpertContextService;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.skill.application.service.ChatSkillContextService;
import com.codingx.tool.application.service.ChatToolExecutionResult;
import com.codingx.tool.application.service.ChatToolExecutionService;
import com.codingx.tool.application.service.ChatToolExecutionContext;
import com.codingx.tool.application.service.ChatToolSpec;
import com.codingx.tool.application.service.ChatToolSpecService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证聊天主流程能够处理模型自主发起的 Codex 本地工具调用。
 */
@ExtendWith(MockitoExtension.class)
class ChatApplicationToolCallFlowTest {

    @Mock private ChatConversationRepository chatConversationRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatExecutionRunRepository chatExecutionRunRepository;
    @Mock private ChatExecutionStepRepository chatExecutionStepRepository;
    @Mock private AiChatClient aiChatClient;
    @Mock private ChatStreamPublisher chatStreamPublisher;
    @Mock private ChatRuntimeGuardService chatRuntimeGuardService;
    @Mock private ConversationTitleService conversationTitleService;
    @Mock private ConversationSummaryService conversationSummaryService;
    @Mock private ConversationRewriteService conversationRewriteService;
    @Mock private ConversationIntentService conversationIntentService;
    @Mock private PromptTemplateLoader promptTemplateLoader;
    @Mock private ChatIntentNodeRepository chatIntentNodeRepository;
    @Mock private WebSearchExecutionService webSearchExecutionService;
    @Mock private SearchReferenceCollector searchReferenceCollector;
    @Mock private DocumentArtifactService documentArtifactService;
    @Mock private ConversationTraceRecordService conversationTraceRecordService;
    @Mock private ChatMcpRepository chatMcpRepository;
    @Mock private ChatAttachmentService chatAttachmentService;
    @Mock private ChatSkillContextService chatSkillContextService;
    @Mock private ChatExpertContextService chatExpertContextService;
    @Mock private com.codingx.common.support.ai.TokenCounterService tokenCounterService;
    @Mock private com.codingx.common.support.ai.LlmResponseCleaner llmResponseCleaner;
    @Mock private RuntimeSettingService runtimeSettingService;
    @Mock private ChatToolSpecService chatToolSpecService;
    @Mock private ChatToolExecutionService chatToolExecutionService;
    @Mock private ChatWorkspaceBindingService chatWorkspaceBindingService;

    @InjectMocks
    private ChatApplicationService chatApplicationService;

    /**
     * 工具调用轮次上限由系统配置控制，单测中固定为 10，避免默认空值影响流程分支。
     */
    @BeforeEach
    void stubRuntimeToolRoundLimit() {
        when(runtimeSettingService.chatToolMaxRounds()).thenReturn(10);
    }

    /**
     * 模型第一轮发起工具调用后，后端应执行工具并用第二轮模型输出作为最终助手消息。
     *
     * @param tempDir 本地 workspace 临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void sendMessageExecutesModelToolCallAndContinuesWithToolEvidence(@TempDir Path tempDir) throws Exception {
        Long runId = 9401001L;
        ChatExecutionContext.start(runId);
        Path workspace = tempDir.resolve("repo");
        Files.createDirectories(workspace);
        ChatConversation conversation = ChatConversation.create(1L, "Local Tools", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("创建一个本地文件", false, List.of("创建一个本地文件"))
        );
        when(conversationIntentService.route("创建一个本地文件", false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("本地工具调用");
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec(
                "test_sync_tool",
                "同步测试工具",
                Map.of("type", "object", "properties", Map.of("message", Map.of("type", "string")))
            )
        ));
        when(chatToolExecutionService.execute(eq("test_sync_tool"), eq("{\"message\":\"touch\"}"))).thenReturn(
            new ChatToolExecutionResult("test_sync_tool", "工具已执行", Map.of("ok", true))
        );
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            if (modelRound.incrementAndGet() == 1) {
                handler.onToolCall(new AiToolCall("call-1", "test_sync_tool", "{\"message\":\"touch\"}"));
                handler.onComplete();
                return null;
            }
            handler.onDelta("已通过工具完成本地文件操作。");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(1L, "创建一个本地文件", false, List.of(), List.of(), null, workspace.toString(), List.of()),
            1002L
        );

        verify(chatToolExecutionService).execute("test_sync_tool", "{\"message\":\"touch\"}");
        verify(aiChatClient, org.mockito.Mockito.times(2)).streamChatWithTools(any(), eq(false), any(), any());
        ArgumentCaptor<Object> toolEventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(chatStreamPublisher, org.mockito.Mockito.times(2)).publishToolCall(eq(1L), toolEventCaptor.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolEvents = toolEventCaptor.getAllValues().stream()
            .map(value -> (Map<String, Object>) value)
            .toList();
        assertEquals("start", toolEvents.get(0).get("phase"));
        assertEquals("call-1", toolEvents.get(0).get("callId"));
        assertEquals("test_sync_tool", toolEvents.get(0).get("toolId"));
        assertEquals("touch", ((Map<?, ?>) toolEvents.get(0).get("params")).get("message"));
        assertEquals("需要调用 test_sync_tool 获取或处理当前问题所需的信息。", toolEvents.get(0).get("reactThought"));
        assertEquals("调用 test_sync_tool", toolEvents.get(0).get("reactAction"));
        assertEquals("complete", toolEvents.get(1).get("phase"));
        assertEquals("call-1", toolEvents.get(1).get("callId"));
        assertEquals("test_sync_tool", toolEvents.get(1).get("toolId"));
        assertEquals("工具已执行", toolEvents.get(1).get("content"));
        assertEquals("工具返回：工具已执行", toolEvents.get(1).get("reactObservation"));
        assertEquals(Boolean.TRUE, ((Map<?, ?>) toolEvents.get(1).get("resultMetadata")).get("ok"));
        verify(chatStreamPublisher).publishAssistantCompleted(1L, "已通过工具完成本地文件操作。", "本地工具调用");
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        ChatMessage assistantMessage = messageCaptor.getAllValues().get(1);
        assertEquals(ChatMessageRole.ASSISTANT, assistantMessage.getRole());
        assertEquals(ChatMessageStatus.COMPLETED, assistantMessage.getStatus());
        assertEquals("已通过工具完成本地文件操作。", assistantMessage.getContent());
        ChatExecutionContext.clear();
    }

    /**
     * 模型在工具调用前已经说出的正文必须保留，工具结果回灌后继续追加后续正文。
     *
     * @param tempDir 本地 workspace 临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void sendMessagePreservesContentBeforeAndAfterModelToolCall(@TempDir Path tempDir) throws Exception {
        Long runId = 9401005L;
        ChatExecutionContext.start(runId);
        Path workspace = tempDir.resolve("repo");
        Files.createDirectories(workspace);
        ChatConversation conversation = ChatConversation.create(5L, "Live Tool Stream", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(5L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(5L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(5L), eq(1002L))).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("检查目录后继续说明", false, List.of("检查目录后继续说明"))
        );
        when(conversationIntentService.route("检查目录后继续说明", false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("实时工具过程");
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("test_sync_tool", "同步测试工具", Map.of("type", "object"))
        ));
        when(chatToolExecutionService.execute(eq("test_sync_tool"), eq("{\"message\":\"pwd\"}"))).thenReturn(
            new ChatToolExecutionResult("test_sync_tool", "D:/code/CodingX", Map.of("exitCode", 0))
        );
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            if (modelRound.incrementAndGet() == 1) {
                handler.onDelta("我先检查当前目录。\n\n");
                handler.onToolCall(new AiToolCall("call-live-1", "test_sync_tool", "{\"message\":\"pwd\"}"));
                handler.onComplete();
                return null;
            }
            handler.onDelta("目录确认后，我继续说明结果。");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(5L, "检查目录后继续说明", false, List.of(), List.of(), null, workspace.toString(), List.of()),
            1002L
        );

        verify(chatStreamPublisher).publishAssistantDelta(5L, "我先检查当前目录。\n\n");
        verify(chatStreamPublisher).publishAssistantDelta(5L, "目录确认后，我继续说明结果。");
        verify(chatStreamPublisher).publishAssistantCompleted(
            5L,
            "我先检查当前目录。\n\n目录确认后，我继续说明结果。",
            "实时工具过程"
        );
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        ChatMessage assistantMessage = messageCaptor.getAllValues().get(1);
        assertEquals("我先检查当前目录。\n\n目录确认后，我继续说明结果。", assistantMessage.getContent());
        ChatExecutionContext.clear();
    }

    /**
     * 工具回灌后的下一轮模型必须知道本轮已经流给用户的正文，避免重复输出相同开场白。
     *
     * @param tempDir 本地 workspace 临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void sendMessageFeedsPublishedAssistantContentIntoNextToolRound(@TempDir Path tempDir) throws Exception {
        Long runId = 9401008L;
        ChatExecutionContext.start(runId);
        try {
            Path workspace = tempDir.resolve("repo");
            Files.createDirectories(workspace);
            ChatConversation conversation = ChatConversation.create(8L, "No Repeat Preamble", 1002L, ChatConversationStatus.ACTIVE);
            when(chatConversationRepository.requireById(8L)).thenReturn(conversation);
            when(chatMessageRepository.findByConversationId(8L)).thenReturn(new ArrayList<>());
            when(chatAttachmentService.requireOwnedAttachments(any(), eq(8L), eq(1002L))).thenReturn(List.of());
            when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
                new ConversationRewriteResult("帮我写个简单的日记html", false, List.of("帮我写个简单的日记html"))
            );
            when(conversationIntentService.route("帮我写个简单的日记html", false)).thenReturn(
                new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
            );
            when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
            when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
            when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
            when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
            when(conversationTitleService.generateTitle(any(), any())).thenReturn("日记页面");
            when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
                new ChatToolSpec("test_sync_tool", "同步测试工具", Map.of("type", "object"))
            ));
            when(chatToolExecutionService.execute(eq("test_sync_tool"), eq("{\"message\":\"mkdir\"}"))).thenReturn(
                new ChatToolExecutionResult("test_sync_tool", "目录已创建", Map.of("round", 1))
            );
            AtomicInteger modelRound = new AtomicInteger();
            doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                List<ChatMessage> history = invocation.getArgument(0);
                AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
                if (modelRound.incrementAndGet() == 1) {
                    handler.onDelta("我将为您创建一个简单的日记HTML页面。");
                    handler.onToolCall(new AiToolCall("call-repeat-1", "test_sync_tool", "{\"message\":\"mkdir\"}"));
                    handler.onComplete();
                    return null;
                }
                assertEquals(
                    true,
                    history.stream().anyMatch(message ->
                        message.getRole() == ChatMessageRole.SYSTEM
                            && message.getContent().contains("我将为您创建一个简单的日记HTML页面")
                            && message.getContent().contains("不要重复输出")
                    )
                );
                handler.onDelta("目录已准备好，接下来写入页面。");
                handler.onComplete();
                return null;
            }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

            chatApplicationService.sendMessage(
                new SendChatMessageCommand(8L, "帮我写个简单的日记html", false, List.of(), List.of(), null, workspace.toString(), List.of()),
                1002L
            );

            verify(aiChatClient, org.mockito.Mockito.times(2)).streamChatWithTools(any(), eq(false), any(), any());
            verify(chatStreamPublisher).publishAssistantCompleted(
                8L,
                "我将为您创建一个简单的日记HTML页面。目录已准备好，接下来写入页面。",
                "日记页面"
            );
        } finally {
            ChatExecutionContext.clear();
        }
    }

    /**
     * 深度思考内容是模型真实返回的 reasoning，工具回灌轮次不能清空已收到的 thinking。
     *
     * @param tempDir 本地 workspace 临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void sendMessagePreservesThinkingBeforeModelToolCall(@TempDir Path tempDir) throws Exception {
        Long runId = 9401006L;
        ChatExecutionContext.start(runId);
        Path workspace = tempDir.resolve("repo");
        Files.createDirectories(workspace);
        ChatConversation conversation = ChatConversation.create(6L, "Thinking Tools", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(6L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(6L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(6L), eq(1002L))).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("深度思考后调用工具", false, List.of("深度思考后调用工具"))
        );
        when(conversationIntentService.route("深度思考后调用工具", false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("思考工具过程");
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("test_sync_tool", "同步测试工具", Map.of("type", "object"))
        ));
        when(chatToolExecutionService.execute(eq("test_sync_tool"), eq("{\"message\":\"inspect\"}"))).thenReturn(
            new ChatToolExecutionResult("test_sync_tool", "工具观察结果", Map.of("ok", true))
        );
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            if (modelRound.incrementAndGet() == 1) {
                handler.onThinkingDelta("先判断是否需要工具。");
                handler.onDelta("我先确认一下信息。\n\n");
                handler.onToolCall(new AiToolCall("call-thinking-1", "test_sync_tool", "{\"message\":\"inspect\"}"));
                handler.onComplete();
                return null;
            }
            handler.onThinkingDelta("根据工具结果继续分析。");
            handler.onDelta("工具结果已经确认。");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(true), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(6L, "深度思考后调用工具", true, List.of(), List.of(), null, workspace.toString(), List.of()),
            1002L
        );

        verify(chatStreamPublisher).publishAssistantThinkingDelta(6L, "先判断是否需要工具。");
        verify(chatStreamPublisher).publishAssistantThinkingDelta(6L, "根据工具结果继续分析。");
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        ChatMessage assistantMessage = messageCaptor.getAllValues().get(1);
        assertEquals("先判断是否需要工具。根据工具结果继续分析。", assistantMessage.getThinkingContent());
        ChatExecutionContext.clear();
    }

    /**
     * 第一轮工具完成后仍需继续生成但已达上限时，应失败收口并保留第一轮正文与 thinking。
     *
     * @param tempDir 本地 workspace 临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void sendMessageReportsToolRoundLimitAfterFirstToolRoundReachesLimit(@TempDir Path tempDir) throws Exception {
        Long runId = 9401007L;
        ChatExecutionContext.start(runId);
        try {
            Path workspace = tempDir.resolve("repo");
            Files.createDirectories(workspace);
            ChatConversation conversation = ChatConversation.create(7L, "Tool Round Limit", 1002L, ChatConversationStatus.ACTIVE);
            when(runtimeSettingService.chatToolMaxRounds()).thenReturn(1);
            when(chatConversationRepository.requireById(7L)).thenReturn(conversation);
            when(chatMessageRepository.findByConversationId(7L)).thenReturn(new ArrayList<>());
            when(chatAttachmentService.requireOwnedAttachments(any(), eq(7L), eq(1002L))).thenReturn(List.of());
            when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
                new ConversationRewriteResult("连续调用本地工具", false, List.of("连续调用本地工具"))
            );
            when(conversationIntentService.route("连续调用本地工具", false)).thenReturn(
                new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
            );
            when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
            when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
            when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
            when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
            when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
                new ChatToolSpec("test_sync_tool", "同步测试工具", Map.of("type", "object"))
            ));
            when(chatToolExecutionService.execute(eq("test_sync_tool"), eq("{\"message\":\"first\"}"))).thenReturn(
                new ChatToolExecutionResult("test_sync_tool", "第一轮工具结果", Map.of("round", 1))
            );
            AtomicInteger modelRound = new AtomicInteger();
            doAnswer(invocation -> {
                AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
                int round = modelRound.incrementAndGet();
                if (round > 1) {
                    throw new AssertionError("工具轮次达到上限后不应再次请求模型生成");
                }
                handler.onThinkingDelta("先规划第一步。");
                handler.onDelta("我先执行第一轮工具。\n\n");
                handler.onToolCall(new AiToolCall("call-limit-1", "test_sync_tool", "{\"message\":\"first\"}"));
                handler.onComplete();
                return null;
            }).when(aiChatClient).streamChatWithTools(any(), eq(true), any(), any());

            chatApplicationService.sendMessage(
                new SendChatMessageCommand(7L, "连续调用本地工具", true, List.of(), List.of(), null, workspace.toString(), List.of()),
                1002L
            );

            verify(aiChatClient, org.mockito.Mockito.times(1)).streamChatWithTools(any(), eq(true), any(), any());
            verify(chatToolExecutionService).execute("test_sync_tool", "{\"message\":\"first\"}");
            verify(chatToolExecutionService, org.mockito.Mockito.never()).execute(
                eq("test_sync_tool"),
                eq("{\"message\":\"second\"}")
            );
            verify(chatStreamPublisher).publishError(7L, "本地工具调用轮次超过上限，请收敛工具调用后重试");
            ArgumentCaptor<Object> toolEventCaptor = ArgumentCaptor.forClass(Object.class);
            verify(chatStreamPublisher, org.mockito.Mockito.times(2)).publishToolCall(eq(7L), toolEventCaptor.capture());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolEvents = toolEventCaptor.getAllValues().stream()
                .map(value -> (Map<String, Object>) value)
                .toList();
            assertEquals("start", toolEvents.get(0).get("phase"));
            assertEquals("call-limit-1", toolEvents.get(0).get("callId"));
            assertEquals("test_sync_tool", toolEvents.get(0).get("toolId"));
            assertEquals("complete", toolEvents.get(1).get("phase"));
            assertEquals("call-limit-1", toolEvents.get(1).get("callId"));
            assertEquals("第一轮工具结果", toolEvents.get(1).get("content"));
            ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
            verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
            ChatMessage failedMessage = messageCaptor.getAllValues().get(1);
            assertEquals(ChatMessageStatus.FAILED, failedMessage.getStatus());
            assertEquals("我先执行第一轮工具。\n\n", failedMessage.getContent());
            assertEquals("先规划第一步。", failedMessage.getThinkingContent());
            assertEquals("本地工具调用轮次超过上限，请收敛工具调用后重试", failedMessage.getErrorMessage());
        } finally {
            ChatExecutionContext.clear();
        }
    }

    /**
     * 当前端没有在单次消息里继续传 repositoryPath 时，应回落到会话 workspace 绑定的目录。
     *
     * @param tempDir 本地 workspace 临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void sendMessageUsesConversationWorkspacePathForModelToolCall(@TempDir Path tempDir) throws Exception {
        Long runId = 9401002L;
        ChatExecutionContext.start(runId);
        Path workspace = tempDir.resolve("workspace-bound-repo");
        Files.createDirectories(workspace);
        ChatConversation conversation = ChatConversation.create(2L, "Workspace Tools", 1002L, 3001L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(2L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(2L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(2L), eq(1002L))).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("通过本地工具操作 workspace 文件", false, List.of("通过本地工具操作 workspace 文件"))
        );
        when(conversationIntentService.route("通过本地工具操作 workspace 文件", false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("workspace 工具调用");
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatWorkspaceBindingService.findRepositoryPathByWorkspaceId(3001L)).thenReturn(java.util.Optional.of(workspace));
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec(
                "test_sync_tool",
                "同步测试工具",
                Map.of("type", "object", "properties", Map.of("message", Map.of("type", "string")))
            )
        ));
        when(chatToolExecutionService.execute(eq("test_sync_tool"), eq("{\"message\":\"workspace\"}"))).thenAnswer(invocation -> {
            assertEquals(workspace.toAbsolutePath().normalize(), ChatToolExecutionContext.currentToolWorkingDirectory().orElseThrow());
            return new ChatToolExecutionResult("test_sync_tool", "workspace 工具已执行", Map.of("ok", true));
        });
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            if (modelRound.incrementAndGet() == 1) {
                handler.onToolCall(new AiToolCall("call-workspace", "test_sync_tool", "{\"message\":\"workspace\"}"));
                handler.onComplete();
                return null;
            }
            handler.onDelta("已在 workspace 执行。");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(2L, "通过本地工具操作 workspace 文件", false, List.of(), List.of(), null, null, List.of()),
            1002L
        );

        verify(chatWorkspaceBindingService).findRepositoryPathByWorkspaceId(3001L);
        verify(chatToolExecutionService).execute("test_sync_tool", "{\"message\":\"workspace\"}");
        verify(chatStreamPublisher).publishAssistantCompleted(2L, "已在 workspace 执行。", "workspace 工具调用");
        ChatExecutionContext.clear();
    }

    /**
     * 图片附件已通过多模态请求体交给视觉模型时，不应继续暴露 view_image。
     * 否则模型会把上传图片误当成本地路径工具调用，导致用户明明上传了图片却返回“图片不存在”。
     */
    @Test
    void sendMessageHidesViewImageToolWhenImageAttachmentAlreadyUploaded() {
        Long runId = 9401004L;
        ChatExecutionContext.start(runId);
        ChatConversation conversation = ChatConversation.create(4L, "Image Upload", 1002L, ChatConversationStatus.ACTIVE);
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
        when(chatConversationRepository.requireById(4L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(4L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(4L), eq(1002L))).thenReturn(List.of(attachment));
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("这张图里是谁", false, List.of("这张图里是谁"))
        );
        when(conversationIntentService.route("这张图里是谁", false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("图片识别");
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("view_image", "读取本地图片", Map.of("type", "object")),
            new ChatToolSpec("test_sync_tool", "同步测试工具", Map.of("type", "object"))
        ));
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            handler.onDelta("这是用户上传的图片。");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(4L, "这张图里是谁", false, List.of(), List.of(), null, null, List.of(5001L)),
            1002L
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatToolSpec>> toolSpecsCaptor = ArgumentCaptor.forClass(List.class);
        verify(aiChatClient).streamChatWithTools(any(), eq(false), toolSpecsCaptor.capture(), any());
        assertEquals(
            List.of("test_sync_tool"),
            toolSpecsCaptor.getValue().stream().map(ChatToolSpec::name).toList()
        );
        verify(chatAttachmentService).bindToMessage(eq(attachment), eq(4L), org.mockito.ArgumentMatchers.any(Long.class), eq(runId));
        ChatExecutionContext.clear();
    }

    /**
     * 模型误调用不可用工具时，主流程应按失败消息收口，而不是把异常抛出到异步链路外层。
     */
    @Test
    void sendMessageRecordsFailureWhenModelToolExecutionFails() {
        Long runId = 9401003L;
        ChatExecutionContext.start(runId);
        ChatConversation conversation = ChatConversation.create(3L, "Tool Failure", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(3L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(3L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(3L), eq(1002L))).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("调用不可用工具", false, List.of("调用不可用工具"))
        );
        when(conversationIntentService.route("调用不可用工具", false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("spawn_agent", "不可用工具", Map.of("type", "object"))
        ));
        when(chatToolExecutionService.execute(eq("spawn_agent"), eq("{\"message\":\"x\"}"))).thenThrow(
            new BusinessException("CHAT_TOOL_CODEX_RUNTIME_UNAVAILABLE", "spawn_agent 暂未接入真实 Codex 运行时")
        );
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            handler.onThinkingDelta("先判断工具可用性。");
            handler.onDelta("我先尝试调用工具。\n\n");
            handler.onToolCall(new AiToolCall("call-failed", "spawn_agent", "{\"message\":\"x\"}"));
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(true), any(), any());

        assertDoesNotThrow(() -> chatApplicationService.sendMessage(
            new SendChatMessageCommand(3L, "调用不可用工具", true, List.of(), List.of(), null, null, List.of()),
            1002L
        ));

        verify(chatStreamPublisher).publishError(3L, "spawn_agent 暂未接入真实 Codex 运行时");
        ArgumentCaptor<Object> toolEventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(chatStreamPublisher, org.mockito.Mockito.times(2)).publishToolCall(eq(3L), toolEventCaptor.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolEvents = toolEventCaptor.getAllValues().stream()
            .map(value -> (Map<String, Object>) value)
            .toList();
        assertEquals("start", toolEvents.get(0).get("phase"));
        assertEquals("spawn_agent", toolEvents.get(0).get("toolId"));
        assertEquals("error", toolEvents.get(1).get("phase"));
        assertEquals("spawn_agent", toolEvents.get(1).get("toolId"));
        assertEquals("spawn_agent 暂未接入真实 Codex 运行时", toolEvents.get(1).get("errorMessage"));
        assertEquals("工具异常：spawn_agent 暂未接入真实 Codex 运行时", toolEvents.get(1).get("reactObservation"));
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        ChatMessage failedMessage = messageCaptor.getAllValues().get(1);
        assertEquals(ChatMessageStatus.FAILED, failedMessage.getStatus());
        assertEquals("我先尝试调用工具。\n\n", failedMessage.getContent());
        assertEquals("先判断工具可用性。", failedMessage.getThinkingContent());
        ChatExecutionContext.clear();
    }
}
