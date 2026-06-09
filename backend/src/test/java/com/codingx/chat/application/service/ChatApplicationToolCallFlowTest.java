package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatExecutionStep;
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
import com.codingx.common.support.ai.AiToolCallDelta;
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
        lenient().when(runtimeSettingService.planModeExecutionMinToolRounds()).thenReturn(20);
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
        ArgumentCaptor<ChatExecutionStep> stepCaptor = ArgumentCaptor.forClass(ChatExecutionStep.class);
        verify(chatExecutionStepRepository, org.mockito.Mockito.atLeastOnce()).save(stepCaptor.capture());
        ChatExecutionStep toolStep = stepCaptor.getAllValues().stream()
            .filter(step -> "tool".equals(step.getStepType()))
            .findFirst()
            .orElseThrow();
        assertEquals("tool", toolStep.getStepType());
        assertNotNull(toolStep.getMetadataJson());
        JSONObject toolStepMetadata = JSONUtil.parseObj(toolStep.getMetadataJson());
        assertEquals("call-1", toolStepMetadata.getStr("callId"));
        assertEquals("test_sync_tool", toolStepMetadata.getStr("toolId"));
        assertEquals("同步测试工具", toolStepMetadata.getStr("displayName"));
        assertEquals("touch", toolStepMetadata.getJSONObject("params").getStr("message"));
        assertEquals("工具已执行", toolStepMetadata.getStr("rawResult"));
        assertEquals(Boolean.TRUE, toolStepMetadata.getJSONObject("resultMetadata").getBool("ok"));
        verify(chatStreamPublisher).publishAssistantCompleted(
            eq(1L),
            any(),
            eq("已通过工具完成本地文件操作。"),
            eq("本地工具调用")
        );
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        ChatMessage assistantMessage = messageCaptor.getAllValues().get(1);
        assertEquals(ChatMessageRole.ASSISTANT, assistantMessage.getRole());
        assertEquals(ChatMessageStatus.COMPLETED, assistantMessage.getStatus());
        assertEquals("已通过工具完成本地文件操作。", assistantMessage.getContent());
        ChatExecutionContext.clear();
    }

    /**
     * 带工具调用的模型轮次可能输出“现在执行”等内部过程正文；这些正文不能提前展示给用户。
     *
     * @param tempDir 本地 workspace 临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void sendMessageSuppressesToolRoundNarrationUntilFinalRound(@TempDir Path tempDir) throws Exception {
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
                handler.onDelta("现在执行：在当前 workspace 中检查目录。\n\n");
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

        verify(chatStreamPublisher, never()).publishAssistantDelta(5L, "现在执行：在当前 workspace 中检查目录。\n\n");
        verify(chatStreamPublisher).publishAssistantDelta(5L, "目录确认后，我继续说明结果。");
        verify(chatStreamPublisher).publishAssistantCompleted(
            eq(5L),
            any(),
            eq("目录确认后，我继续说明结果。"),
            eq("实时工具过程")
        );
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        ChatMessage assistantMessage = messageCaptor.getAllValues().get(1);
        assertEquals("目录确认后，我继续说明结果。", assistantMessage.getContent());
        ChatExecutionContext.clear();
    }

    /**
     * 工具调用轮次正文尚未对用户可见，不能作为“已发布正文”回灌给下一轮模型。
     *
     * @param tempDir 本地 workspace 临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void sendMessageDoesNotFeedSuppressedToolRoundContentIntoNextToolRound(@TempDir Path tempDir) throws Exception {
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
                    handler.onDelta("现在执行：准备创建日记 HTML 页面。");
                    handler.onToolCall(new AiToolCall("call-repeat-1", "test_sync_tool", "{\"message\":\"mkdir\"}"));
                    handler.onComplete();
                    return null;
                }
                assertEquals(
                    false,
                    history.stream().anyMatch(message ->
                        message.getRole() == ChatMessageRole.SYSTEM
                            && message.getContent().contains("现在执行：准备创建日记 HTML 页面")
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
            verify(chatStreamPublisher, never()).publishAssistantDelta(8L, "现在执行：准备创建日记 HTML 页面。");
            verify(chatStreamPublisher).publishAssistantCompleted(
                eq(8L),
                any(),
                eq("目录已准备好，接下来写入页面。"),
                eq("日记页面")
            );
        } finally {
            ChatExecutionContext.clear();
        }
    }

    /**
     * 工具回灌后的下一轮模型可能先输出较长的执行叙述，随后才发起真实 tool_call。
     * 业务约束：这类“我将/下一步/现在执行”文本仍是内部过程，不能因为长度超过短进度阈值就提前发布到用户侧。
     *
     * @param tempDir 本地 workspace 临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void sendMessageSuppressesLongToolNarrationBeforeLaterToolCall(@TempDir Path tempDir) throws Exception {
        Long runId = 9401017L;
        ChatExecutionContext.start(runId);
        try {
            Path workspace = tempDir.resolve("repo");
            Files.createDirectories(workspace);
            ChatConversation conversation = ChatConversation.create(17L, "Long Tool Narration", 1002L, ChatConversationStatus.ACTIVE);
            when(chatConversationRepository.requireById(17L)).thenReturn(conversation);
            when(chatMessageRepository.findByConversationId(17L)).thenReturn(new ArrayList<>());
            when(chatAttachmentService.requireOwnedAttachments(any(), eq(17L), eq(1002L))).thenReturn(List.of());
            when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
                new ConversationRewriteResult("丰富 snake-game.html 功能", false, List.of("丰富 snake-game.html 功能"))
            );
            when(conversationIntentService.route("丰富 snake-game.html 功能", false)).thenReturn(
                new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
            );
            when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
            when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
            when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
            when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
            when(conversationTitleService.generateTitle(any(), any())).thenReturn("贪吃蛇增强");
            when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
                new ChatToolSpec("read", "读取文件", Map.of("type", "object"))
            ));
            when(chatToolExecutionService.execute(eq("read"), any())).thenAnswer(invocation ->
                new ChatToolExecutionResult("read", "snake-game.html 内容片段", Map.of("path", "snake-game.html"))
            );
            AtomicInteger modelRound = new AtomicInteger();
            doAnswer(invocation -> {
                AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
                int round = modelRound.incrementAndGet();
                if (round == 1) {
                    handler.onToolCall(new AiToolCall("call-read-1", "read", "{\"path\":\"snake-game.html\"}"));
                    handler.onComplete();
                    return null;
                }
                if (round == 2) {
                    handler.onDelta("""
                        我们先确认 `snake-game.html` 的完整内容，尤其是 `<script>` 部分。
                        ✅ 下一步行动：完整读取文件，定位并分析现有 JS 逻辑。
                        现在执行完整读取：
                        """);
                    handler.onToolCall(new AiToolCall("call-read-2", "read", "{\"path\":\"snake-game.html\",\"limit\":1000}"));
                    handler.onComplete();
                    return null;
                }
                handler.onDelta("已读取完整文件，接下来可以安全增强贪吃蛇功能。");
                handler.onComplete();
                return null;
            }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

            chatApplicationService.sendMessage(
                new SendChatMessageCommand(17L, "丰富 snake-game.html 功能", false, List.of(), List.of(), null, workspace.toString(), List.of()),
                1002L
            );

            verify(chatStreamPublisher, never()).publishAssistantDelta(
                eq(17L),
                org.mockito.ArgumentMatchers.contains("我们先确认")
            );
            verify(chatStreamPublisher).publishAssistantDelta(17L, "已读取完整文件，接下来可以安全增强贪吃蛇功能。");
            verify(chatStreamPublisher).publishAssistantCompleted(
                eq(17L),
                any(),
                eq("已读取完整文件，接下来可以安全增强贪吃蛇功能。"),
                eq("贪吃蛇增强")
            );
            ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
            verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
            ChatMessage assistantMessage = messageCaptor.getAllValues().get(1);
            assertEquals("已读取完整文件，接下来可以安全增强贪吃蛇功能。", assistantMessage.getContent());
        } finally {
            ChatExecutionContext.clear();
        }
    }

    /**
     * 模型生成大段 write.content 时，应在完整工具调用完成前持续推送参数进度，让前端先展示“正在编辑文件”。
     *
     * @param tempDir 本地 workspace 临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void sendMessagePublishesWriteToolArgumentProgressBeforeToolCompletes(@TempDir Path tempDir) throws Exception {
        Long runId = 9401019L;
        ChatExecutionContext.start(runId);
        try {
            Path workspace = tempDir.resolve("repo");
            Files.createDirectories(workspace);
            ChatConversation conversation = ChatConversation.create(19L, "Write Progress", 1002L, ChatConversationStatus.ACTIVE);
            when(chatConversationRepository.requireById(19L)).thenReturn(conversation);
            when(chatMessageRepository.findByConversationId(19L)).thenReturn(new ArrayList<>());
            when(chatAttachmentService.requireOwnedAttachments(any(), eq(19L), eq(1002L))).thenReturn(List.of());
            when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
                new ConversationRewriteResult("帮我写个肉鸽贪吃蛇html", false, List.of("帮我写个肉鸽贪吃蛇html"))
            );
            when(conversationIntentService.route("帮我写个肉鸽贪吃蛇html", false)).thenReturn(
                new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
            );
            when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
            when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
            when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
            when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
            when(conversationTitleService.generateTitle(any(), any())).thenReturn("肉鸽贪吃蛇");
            when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(promptTemplateLoader.render(eq("local-tool-evidence-context"), any())).thenReturn("工具证据上下文");
            when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
                new ChatToolSpec("write", "写文件", Map.of("type", "object"))
            ));
            when(chatToolExecutionService.execute(eq("write"), any())).thenReturn(
                new ChatToolExecutionResult("write", "文件已写入", Map.of(
                    "path", "rogue_snake.html",
                    "diffSummary", Map.of("filesChanged", 1, "additions", 2, "deletions", 0)
                ))
            );
            AtomicInteger modelRound = new AtomicInteger();
            doAnswer(invocation -> {
                AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
                if (modelRound.incrementAndGet() == 1) {
                    String firstContent = "<!DOCTYPE html>\\n<html" + "x".repeat(820);
                    String secondContent = firstContent + "streaming-second-marker" + "y".repeat(145);
                    String thirdContent = secondContent + "instant-tiny-marker";
                    String firstArguments = "{\"path\":\"rogue_snake.html\",\"content\":\"" + firstContent;
                    String secondArguments = "{\"path\":\"rogue_snake.html\",\"content\":\"" + secondContent;
                    String thirdArguments = "{\"path\":\"rogue_snake.html\",\"content\":\"" + thirdContent;
                    handler.onToolCallDelta(new AiToolCallDelta(
                        "call-write-1",
                        "write",
                        firstArguments,
                        firstArguments
                    ));
                    handler.onToolCallDelta(new AiToolCallDelta(
                        "call-write-1",
                        "write",
                        secondArguments.substring(firstArguments.length()),
                        secondArguments
                    ));
                    handler.onToolCallDelta(new AiToolCallDelta(
                        "call-write-1",
                        "write",
                        thirdArguments.substring(secondArguments.length()),
                        thirdArguments
                    ));
                    handler.onToolCall(new AiToolCall(
                        "call-write-1",
                        "write",
                        "{\"path\":\"rogue_snake.html\",\"content\":\"<!DOCTYPE html>\\n<html></html>\"}"
                    ));
                    handler.onComplete();
                    return null;
                }
                handler.onDelta("已写入 `rogue_snake.html`。");
                handler.onComplete();
                return null;
            }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

            chatApplicationService.sendMessage(
                new SendChatMessageCommand(19L, "帮我写个肉鸽贪吃蛇html", false, List.of(), List.of(), null, workspace.toString(), List.of()),
                1002L
            );

            ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
            verify(chatStreamPublisher, org.mockito.Mockito.atLeastOnce()).publishToolCall(eq(19L), payloadCaptor.capture());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> payloads = payloadCaptor.getAllValues().stream()
                .filter(Map.class::isInstance)
                .map(payload -> (Map<String, Object>) payload)
                .toList();
            List<Map<String, Object>> progressPayloads = payloads.stream()
                .filter(payload -> "progress".equals(payload.get("phase")))
                .toList();
            assertTrue(progressPayloads.size() >= 3);
            Map<String, Object> progressPayload = progressPayloads.get(0);
            assertEquals("write", progressPayload.get("toolId"));
            assertEquals("正在编辑 rogue_snake.html", progressPayload.get("reactAction"));
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) progressPayload.get("params");
            assertEquals("rogue_snake.html", params.get("path"));
            assertTrue(String.valueOf(params.get("content")).contains("<!DOCTYPE html>"));
            @SuppressWarnings("unchecked")
            Map<String, Object> latestParams = (Map<String, Object>) progressPayloads.get(2).get("params");
            assertTrue(String.valueOf(latestParams.get("content")).contains("instant-tiny-marker"));
        } finally {
            ChatExecutionContext.clear();
        }
    }

    /**
     * 历史中已经落库的内部执行叙述不能继续作为模型上下文，否则用户回复“同意/继续”会触发重复读取并打满工具轮次。
     *
     * @param tempDir 本地 workspace 临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void sendMessageFiltersPersistedToolNarrationFromModelHistory(@TempDir Path tempDir) throws Exception {
        Long runId = 9401018L;
        ChatExecutionContext.start(runId);
        try {
            Path workspace = tempDir.resolve("repo");
            Files.createDirectories(workspace);
            ChatConversation conversation = ChatConversation.create(18L, "Polluted History", 1002L, ChatConversationStatus.ACTIVE);
            ChatMessage previousUserMessage = ChatMessage.userMessage(18L, "那个贪吃蛇帮我丰富一下功能");
            ChatMessage pollutedAssistantMessage = ChatMessage.assistantMessage(
                18L,
                """
                    我们先确认 `snake-game.html` 的完整内容，尤其是 `<script>` 部分。
                    ✅ 下一步行动：完整读取文件并分析现有 JS 逻辑。
                    现在执行完整读取：
                    """,
                ChatMessageStatus.COMPLETED,
                null,
                null,
                null
            );
            when(chatConversationRepository.requireById(18L)).thenReturn(conversation);
            when(chatMessageRepository.findByConversationId(18L)).thenReturn(new ArrayList<>(List.of(
                previousUserMessage,
                pollutedAssistantMessage
            )));
            when(chatAttachmentService.requireOwnedAttachments(any(), eq(18L), eq(1002L))).thenReturn(List.of());
            when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
                new ConversationRewriteResult("同意", false, List.of("同意"))
            );
            when(conversationIntentService.route("同意", false)).thenReturn(
                new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
            );
            when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
            when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
            when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
            when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
            when(conversationTitleService.generateTitle(any(), any())).thenReturn("贪吃蛇增强");
            when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
                new ChatToolSpec("read", "读取文件", Map.of("type", "object"))
            ));
            doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                List<ChatMessage> history = invocation.getArgument(0);
                assertFalse(history.stream().anyMatch(message ->
                    message.getRole() == ChatMessageRole.ASSISTANT
                        && message.getContent().contains("现在执行完整读取")
                ));
                AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
                handler.onDelta("可以，接下来我会基于现有文件直接给出增强结果。");
                handler.onComplete();
                return null;
            }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

            chatApplicationService.sendMessage(
                new SendChatMessageCommand(18L, "同意", false, List.of(), List.of(), null, workspace.toString(), List.of()),
                1002L
            );

            verify(chatStreamPublisher).publishAssistantCompleted(
                eq(18L),
                any(),
                eq("可以，接下来我会基于现有文件直接给出增强结果。"),
                eq("贪吃蛇增强")
            );
        } finally {
            ChatExecutionContext.clear();
        }
    }

    /**
     * 同一个写文件目标如果被模型用不同 HTML 内容反复覆盖，应按同一路径去重并收口成功结果。
     * 业务背景：桌面端生成 HTML 时，模型可能每轮都略微改写 content，原始 arguments 不相等会绕过旧去重，
     * 最终真实执行多次 write 并触发工具轮次上限。
     *
     * @param tempDir 本地 workspace 临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void sendMessageStopsRepeatedWriteToSamePathEvenWhenContentChanges(@TempDir Path tempDir) throws Exception {
        Long runId = 9401016L;
        ChatExecutionContext.start(runId);
        try {
            Path workspace = tempDir.resolve("repo");
            Files.createDirectories(workspace);
            ChatConversation conversation = ChatConversation.create(16L, "Repeat Write", 1002L, ChatConversationStatus.ACTIVE);
            when(chatConversationRepository.requireById(16L)).thenReturn(conversation);
            when(chatMessageRepository.findByConversationId(16L)).thenReturn(new ArrayList<>());
            when(chatAttachmentService.requireOwnedAttachments(any(), eq(16L), eq(1002L))).thenReturn(List.of());
            when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
                new ConversationRewriteResult("帮我写一个 note.html", false, List.of("帮我写一个 note.html"))
            );
            when(conversationIntentService.route("帮我写一个 note.html", false)).thenReturn(
                new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
            );
            when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
            when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
            when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
            when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
            when(conversationTitleService.generateTitle(any(), any())).thenReturn("HTML 写入");
            when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
                new ChatToolSpec("write", "写入文件", Map.of("type", "object"))
            ));
            when(chatToolExecutionService.execute(eq("write"), any())).thenAnswer(invocation ->
                new ChatToolExecutionResult("write", "已写入文件: note.html", Map.of("path", "note.html", "bytes", 128))
            );
            AtomicInteger modelRound = new AtomicInteger();
            doAnswer(invocation -> {
                AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
                int round = modelRound.incrementAndGet();
                if (round == 1) {
                    handler.onToolCall(new AiToolCall(
                        "call-write-1",
                        "write",
                        "{\"path\":\"note.html\",\"content\":\"<!DOCTYPE html><html><body>第一版</body></html>\"}"
                    ));
                    handler.onComplete();
                    return null;
                }
                if (round == 2) {
                    handler.onToolCall(new AiToolCall(
                        "call-write-2",
                        "write",
                        "{\"path\":\"note.html\",\"content\":\"<!DOCTYPE html><html><body>第二版</body></html>\"}"
                    ));
                    handler.onComplete();
                    return null;
                }
                throw new AssertionError("同一路径 write 被判定为重复后不应继续请求第三轮模型");
            }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

            chatApplicationService.sendMessage(
                new SendChatMessageCommand(16L, "帮我写一个 note.html", false, List.of(), List.of(), null, workspace.toString(), List.of()),
                1002L
            );

            verify(aiChatClient, org.mockito.Mockito.times(2)).streamChatWithTools(any(), eq(false), any(), any());
            verify(chatToolExecutionService, org.mockito.Mockito.times(1)).execute(eq("write"), any());
            verify(chatStreamPublisher, never()).publishError(eq(16L), any());
            verify(chatStreamPublisher).publishAssistantCompleted(
                eq(16L),
                any(),
                eq("已完成，文件已写入 `note.html`。"),
                eq("HTML 写入")
            );
            ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
            verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
            ChatMessage assistantMessage = messageCaptor.getAllValues().get(1);
            assertEquals(ChatMessageStatus.COMPLETED, assistantMessage.getStatus());
            assertEquals("已完成，文件已写入 `note.html`。", assistantMessage.getContent());
        } finally {
            ChatExecutionContext.clear();
        }
    }

    /**
     * 同参只读工具重复调用时，应退出工具模式并转为无工具最终生成，避免 read/ReadFile 循环打满工具轮次上限。
     * 业务背景：模型可能先用别名 ReadFile 读取文件，再用 read 读取同一路径；如果继续把“重复已跳过”作为工具结果回灌，
     * 模型会再次请求相同 read，用户侧看起来只调用少数工具，后端却跑满 20 轮。
     *
     * @param tempDir 本地 workspace 临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void sendMessageCompletesRepeatedReadWithPlainStream(@TempDir Path tempDir) throws Exception {
        Long runId = 9401019L;
        ChatExecutionContext.start(runId);
        try {
            Path workspace = tempDir.resolve("repo");
            Files.createDirectories(workspace);
            ChatConversation conversation = ChatConversation.create(19L, "Repeat Read", 1002L, ChatConversationStatus.ACTIVE);
            when(chatConversationRepository.requireById(19L)).thenReturn(conversation);
            when(chatMessageRepository.findByConversationId(19L)).thenReturn(new ArrayList<>());
            when(chatAttachmentService.requireOwnedAttachments(any(), eq(19L), eq(1002L))).thenReturn(List.of());
            when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
                new ConversationRewriteResult("继续分析 README", false, List.of("继续分析 README"))
            );
            when(conversationIntentService.route("继续分析 README", false)).thenReturn(
                new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
            );
            when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
            when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
            when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
            when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
            when(conversationTitleService.generateTitle(any(), any())).thenReturn("README 分析");
            when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
                new ChatToolSpec("read", "读取文件", Map.of("type", "object"))
            ));
            when(chatToolExecutionService.execute(eq("read"), eq("{\"path\":\"README.md\"}"))).thenReturn(
                new ChatToolExecutionResult("read", "README.md 内容片段", Map.of("path", "README.md"))
            );
            AtomicInteger modelRound = new AtomicInteger();
            doAnswer(invocation -> {
                AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
                int round = modelRound.incrementAndGet();
                if (round == 1) {
                    handler.onToolCall(new AiToolCall("call-read-1", "read", "{\"path\":\"README.md\"}"));
                    handler.onComplete();
                    return null;
                }
                if (round == 2) {
                    handler.onToolCall(new AiToolCall("call-read-2", "read", "{\"path\":\"README.md\"}"));
                    handler.onComplete();
                    return null;
                }
                throw new AssertionError("重复 read 收口后不应继续请求第三轮工具模型");
            }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());
            doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                List<ChatMessage> history = invocation.getArgument(0);
                assertTrue(history.stream().anyMatch(message ->
                    message.getRole() == ChatMessageRole.SYSTEM
                        && message.getContent().contains("重复本地工具调用已拦截")
                ));
                assertTrue(history.stream().anyMatch(message -> message.getContent().contains("README.md 内容片段")));
                AiChatClient.StreamHandler handler = invocation.getArgument(2);
                handler.onDelta("已基于 README 内容继续分析。");
                handler.onComplete();
                return null;
            }).when(aiChatClient).streamChat(any(), eq(false), any());

            chatApplicationService.sendMessage(
                new SendChatMessageCommand(19L, "继续分析 README", false, List.of(), List.of(), null, workspace.toString(), List.of()),
                1002L
            );

            verify(aiChatClient, org.mockito.Mockito.times(2)).streamChatWithTools(any(), eq(false), any(), any());
            verify(aiChatClient).streamChat(any(), eq(false), any());
            verify(chatToolExecutionService, org.mockito.Mockito.times(1)).execute("read", "{\"path\":\"README.md\"}");
            verify(chatStreamPublisher, never()).publishError(eq(19L), any());
            verify(chatStreamPublisher).publishAssistantCompleted(
                eq(19L),
                any(),
                eq("已基于 README 内容继续分析。"),
                eq("README 分析")
            );
            ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
            verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
            ChatMessage assistantMessage = messageCaptor.getAllValues().get(1);
            assertEquals(ChatMessageStatus.COMPLETED, assistantMessage.getStatus());
            assertEquals("已基于 README 内容继续分析。", assistantMessage.getContent());
        } finally {
            ChatExecutionContext.clear();
        }
    }

    /**
     * 工具回灌后的模型可能只输出“正在执行...”这类进度句且不再发起 tool_call；这不是最终答案。
     *
     * @param tempDir 本地 workspace 临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void sendMessageSuppressesProgressOnlyFinalRoundAfterToolCall(@TempDir Path tempDir) throws Exception {
        Long runId = 9401010L;
        ChatExecutionContext.start(runId);
        try {
            Path workspace = tempDir.resolve("repo");
            Files.createDirectories(workspace);
            ChatConversation conversation = ChatConversation.create(10L, "Progress Only", 1002L, ChatConversationStatus.ACTIVE);
            when(chatConversationRepository.requireById(10L)).thenReturn(conversation);
            when(chatMessageRepository.findByConversationId(10L)).thenReturn(new ArrayList<>());
            when(chatAttachmentService.requireOwnedAttachments(any(), eq(10L), eq(1002L))).thenReturn(List.of());
            when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
                new ConversationRewriteResult("访问订阅页", false, List.of("访问订阅页"))
            );
            when(conversationIntentService.route("访问订阅页", false)).thenReturn(
                new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
            );
            when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
            when(chatSkillContextService.buildSkillContext(List.of("web-access"))).thenReturn("web-access skill context");
            when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
            when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
            when(conversationTitleService.generateTitle(any(), any())).thenReturn("订阅页信息");
            when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
                new ChatToolSpec("test_sync_tool", "同步测试工具", Map.of("type", "object"))
            ));
            when(chatToolExecutionService.execute(eq("test_sync_tool"), eq("{\"message\":\"open\"}"))).thenReturn(
                new ChatToolExecutionResult("test_sync_tool", "页面标题 Lucen Subscriptions", Map.of("ok", true))
            );
            AtomicInteger modelRound = new AtomicInteger();
            doAnswer(invocation -> {
                AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
                int round = modelRound.incrementAndGet();
                if (round == 1) {
                    handler.onToolCall(new AiToolCall("call-open", "test_sync_tool", "{\"message\":\"open\"}"));
                    handler.onComplete();
                    return null;
                }
                if (round == 2) {
                    handler.onDelta("正在执行页面信息读取...");
                    handler.onComplete();
                    return null;
                }
                handler.onDelta("订阅页已打开，页面标题是 Lucen Subscriptions。");
                handler.onComplete();
                return null;
            }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

            chatApplicationService.sendMessage(
                new SendChatMessageCommand(10L, "访问订阅页", false, List.of(), List.of("web-access"), null, workspace.toString(), List.of()),
                1002L
            );

            verify(chatStreamPublisher, never()).publishAssistantDelta(10L, "正在执行页面信息读取...");
            verify(chatStreamPublisher).publishAssistantDelta(10L, "订阅页已打开，页面标题是 Lucen Subscriptions。");
            verify(chatStreamPublisher).publishAssistantCompleted(
                eq(10L),
                any(),
                eq("订阅页已打开，页面标题是 Lucen Subscriptions。"),
                eq("订阅页信息")
            );
        } finally {
            ChatExecutionContext.clear();
        }
    }

    /**
     * 工具执行后的最终回答必须沿用 SSE 增量发布，不能等模型流结束后一次性回放。
     *
     * @param tempDir 本地 workspace 临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void sendMessagePublishesFinalToolAnswerDeltasBeforeModelStreamCompletes(@TempDir Path tempDir) throws Exception {
        Long runId = 9401011L;
        ChatExecutionContext.start(runId);
        try {
            Path workspace = tempDir.resolve("repo");
            Files.createDirectories(workspace);
            ChatConversation conversation = ChatConversation.create(11L, "Live Final Answer", 1002L, ChatConversationStatus.ACTIVE);
            when(chatConversationRepository.requireById(11L)).thenReturn(conversation);
            when(chatMessageRepository.findByConversationId(11L)).thenReturn(new ArrayList<>());
            when(chatAttachmentService.requireOwnedAttachments(any(), eq(11L), eq(1002L))).thenReturn(List.of());
            when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
                new ConversationRewriteResult("搜索后流式回答", false, List.of("搜索后流式回答"))
            );
            when(conversationIntentService.route("搜索后流式回答", false)).thenReturn(
                new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
            );
            when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
            when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
            when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
            when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
            when(conversationTitleService.generateTitle(any(), any())).thenReturn("搜索后回答");
            when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
                new ChatToolSpec("test_sync_tool", "同步测试工具", Map.of("type", "object"))
            ));
            when(chatToolExecutionService.execute(eq("test_sync_tool"), eq("{\"message\":\"search\"}"))).thenReturn(
                new ChatToolExecutionResult("test_sync_tool", "搜索结果摘要", Map.of("ok", true))
            );
            AtomicInteger modelRound = new AtomicInteger();
            doAnswer(invocation -> {
                AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
                int round = modelRound.incrementAndGet();
                if (round == 1) {
                    handler.onToolCall(new AiToolCall("call-search", "test_sync_tool", "{\"message\":\"search\"}"));
                    handler.onComplete();
                    return null;
                }
                handler.onDelta("第一段");
                // 关键断言：模型流尚未 complete 时，最终回答的第一段已经发布给前端。
                verify(chatStreamPublisher).publishAssistantDelta(11L, "第一段");
                handler.onDelta("第二段");
                verify(chatStreamPublisher).publishAssistantDelta(11L, "第二段");
                handler.onComplete();
                return null;
            }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

            chatApplicationService.sendMessage(
                new SendChatMessageCommand(11L, "搜索后流式回答", false, List.of(), List.of(), null, workspace.toString(), List.of()),
                1002L
            );

            verify(chatStreamPublisher).publishAssistantCompleted(
                eq(11L),
                any(),
                eq("第一段第二段"),
                eq("搜索后回答")
            );
        } finally {
            ChatExecutionContext.clear();
        }
    }

    /**
     * 进度说明可能被模型拆成多个 delta；在完整进度句确认前也不能把半句过程文案推给用户。
     *
     * @param tempDir 本地 workspace 临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void sendMessageSuppressesSplitProgressOnlyFinalRoundAfterToolCall(@TempDir Path tempDir) throws Exception {
        Long runId = 9401012L;
        ChatExecutionContext.start(runId);
        try {
            Path workspace = tempDir.resolve("repo");
            Files.createDirectories(workspace);
            ChatConversation conversation = ChatConversation.create(12L, "Split Progress", 1002L, ChatConversationStatus.ACTIVE);
            when(chatConversationRepository.requireById(12L)).thenReturn(conversation);
            when(chatMessageRepository.findByConversationId(12L)).thenReturn(new ArrayList<>());
            when(chatAttachmentService.requireOwnedAttachments(any(), eq(12L), eq(1002L))).thenReturn(List.of());
            when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
                new ConversationRewriteResult("访问订阅页", false, List.of("访问订阅页"))
            );
            when(conversationIntentService.route("访问订阅页", false)).thenReturn(
                new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
            );
            when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
            when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
            when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
            when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
            when(conversationTitleService.generateTitle(any(), any())).thenReturn("拆分进度");
            when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
                new ChatToolSpec("test_sync_tool", "同步测试工具", Map.of("type", "object"))
            ));
            when(chatToolExecutionService.execute(eq("test_sync_tool"), eq("{\"message\":\"open\"}"))).thenReturn(
                new ChatToolExecutionResult("test_sync_tool", "页面标题 Lucen Subscriptions", Map.of("ok", true))
            );
            AtomicInteger modelRound = new AtomicInteger();
            doAnswer(invocation -> {
                AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
                int round = modelRound.incrementAndGet();
                if (round == 1) {
                    handler.onToolCall(new AiToolCall("call-open-split", "test_sync_tool", "{\"message\":\"open\"}"));
                    handler.onComplete();
                    return null;
                }
                if (round == 2) {
                    handler.onDelta("正在执行");
                    verify(chatStreamPublisher, never()).publishAssistantDelta(12L, "正在执行");
                    handler.onDelta("页面信息读取...");
                    handler.onComplete();
                    return null;
                }
                handler.onDelta("订阅页已打开，页面标题是 Lucen Subscriptions。");
                handler.onComplete();
                return null;
            }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

            chatApplicationService.sendMessage(
                new SendChatMessageCommand(12L, "访问订阅页", false, List.of(), List.of(), null, workspace.toString(), List.of()),
                1002L
            );

            verify(chatStreamPublisher, never()).publishAssistantDelta(12L, "正在执行");
            verify(chatStreamPublisher, never()).publishAssistantDelta(12L, "页面信息读取...");
            verify(chatStreamPublisher).publishAssistantDelta(12L, "订阅页已打开，页面标题是 Lucen Subscriptions。");
            verify(chatStreamPublisher).publishAssistantCompleted(
                eq(12L),
                any(),
                eq("订阅页已打开，页面标题是 Lucen Subscriptions。"),
                eq("拆分进度")
            );
        } finally {
            ChatExecutionContext.clear();
        }
    }

    /**
     * 工具回灌后模型可能只输出“现在执行 + 具体命令列表”，但没有继续发起 tool_call。
     * 这类正文仍是内部执行计划，不是用户可见结果；后端必须隐藏并推动模型基于已有工具证据收口。
     *
     * @param tempDir 本地 workspace 临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void sendMessageSuppressesCommandPlanOnlyFinalRoundAfterToolCall(@TempDir Path tempDir) throws Exception {
        Long runId = 9401013L;
        ChatExecutionContext.start(runId);
        try {
            Path workspace = tempDir.resolve("repo");
            Files.createDirectories(workspace);
            ChatConversation conversation = ChatConversation.create(13L, "Command Plan", 1002L, ChatConversationStatus.ACTIVE);
            when(chatConversationRepository.requireById(13L)).thenReturn(conversation);
            when(chatMessageRepository.findByConversationId(13L)).thenReturn(new ArrayList<>());
            when(chatAttachmentService.requireOwnedAttachments(any(), eq(13L), eq(1002L))).thenReturn(List.of());
            when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
                new ConversationRewriteResult("连接订阅页", false, List.of("连接订阅页"))
            );
            when(conversationIntentService.route("连接订阅页", false)).thenReturn(
                new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
            );
            when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
            when(chatSkillContextService.buildSkillContext(List.of("web-access"))).thenReturn("web-access skill context");
            when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
            when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
            when(conversationTitleService.generateTitle(any(), any())).thenReturn("订阅页复用");
            when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
                new ChatToolSpec("bash", "PowerShell 命令", Map.of("type", "object"))
            ));
            when(chatToolExecutionService.execute(eq("bash"), any())).thenAnswer(invocation -> {
                String arguments = invocation.getArgument(1);
                if (arguments.contains("Invoke-RestMethod -Uri")) {
                    return new ChatToolExecutionResult("bash", "title: 我的订阅 - lucen\nurl: https://lucen.cc/subscriptions", Map.of("ok", true));
                }
                if (arguments.contains("document.title")) {
                    return new ChatToolExecutionResult("bash", "我的订阅 - lucen", Map.of("ok", true));
                }
                if (arguments.contains("document.body.innerText")) {
                    return new ChatToolExecutionResult("bash", "我的订阅\n套餐\n到期时间", Map.of("ok", true));
                }
                return new ChatToolExecutionResult("bash", "", Map.of("ok", true));
            });
            AtomicInteger modelRound = new AtomicInteger();
            doAnswer(invocation -> {
                AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
                int round = modelRound.incrementAndGet();
                if (round == 1) {
                    handler.onToolCall(new AiToolCall("call-targets", "bash", "{\"command\":\"Invoke-RestMethod http://localhost:3456/targets\"}"));
                    handler.onComplete();
                    return null;
                }
                if (round == 2) {
                    handler.onDelta("""
                        现在执行：
                        - `Invoke-RestMethod -Uri "http://localhost:3456/info?target=0DD1D69470DAD7AF8466195E89014206"`
                        - `Invoke-WebRequest -Method Post -Body 'document.title' -Uri "http://localhost:3456/eval?target=0DD1D69470DAD7AF8466195E89014206"`
                        """);
                    handler.onComplete();
                    return null;
                }
                handler.onDelta("已复用现有浏览器标签页，目标 URL 为 https://lucen.cc/subscriptions。");
                handler.onComplete();
                return null;
            }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

            chatApplicationService.sendMessage(
                new SendChatMessageCommand(13L, "连接订阅页", false, List.of(), List.of("web-access"), null, workspace.toString(), List.of()),
                1002L
            );

            verify(chatStreamPublisher, never()).publishAssistantDelta(
                eq(13L),
                org.mockito.ArgumentMatchers.contains("Invoke-RestMethod -Uri")
            );
            verify(chatStreamPublisher).publishAssistantDelta(
                13L,
                "已复用现有浏览器标签页，目标 URL 为 https://lucen.cc/subscriptions。"
            );
            verify(chatStreamPublisher).publishAssistantCompleted(
                eq(13L),
                any(),
                eq("已复用现有浏览器标签页，目标 URL 为 https://lucen.cc/subscriptions。"),
                eq("订阅页复用")
            );
        } finally {
            ChatExecutionContext.clear();
        }
    }

    /**
     * web-access 场景下模型可能首轮不发工具调用，只把要执行的 CDP 命令写成正文。
     * 后端需要把这类正文当作内部计划隐藏，并通过纠偏提示推动下一轮发起真实本地工具调用。
     *
     * @param tempDir 本地 workspace 临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void sendMessageSuppressesInitialCommandPlanAndRetriesToolCall(@TempDir Path tempDir) throws Exception {
        Long runId = 9401014L;
        ChatExecutionContext.start(runId);
        try {
            Path workspace = tempDir.resolve("repo");
            Files.createDirectories(workspace);
            ChatConversation conversation = ChatConversation.create(14L, "Initial Command Plan", 1002L, ChatConversationStatus.ACTIVE);
            when(chatConversationRepository.requireById(14L)).thenReturn(conversation);
            when(chatMessageRepository.findByConversationId(14L)).thenReturn(new ArrayList<>());
            when(chatAttachmentService.requireOwnedAttachments(any(), eq(14L), eq(1002L))).thenReturn(List.of());
            when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
                new ConversationRewriteResult("连接订阅页", false, List.of("连接订阅页"))
            );
            when(conversationIntentService.route("连接订阅页", false)).thenReturn(
                new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
            );
            when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
            when(chatSkillContextService.buildSkillContext(List.of("web-access"))).thenReturn("web-access skill context");
            when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
            when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
            when(conversationTitleService.generateTitle(any(), any())).thenReturn("订阅页连接");
            when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
                new ChatToolSpec("bash", "PowerShell 命令", Map.of("type", "object"))
            ));
            when(chatToolExecutionService.execute(eq("bash"), any())).thenAnswer(invocation -> {
                String arguments = invocation.getArgument(1);
                if (arguments.contains("Invoke-RestMethod -Uri")) {
                    return new ChatToolExecutionResult("bash", "title: 我的订阅 - lucen\nurl: https://lucen.cc/subscriptions", Map.of("ok", true));
                }
                if (arguments.contains("document.title")) {
                    return new ChatToolExecutionResult("bash", "我的订阅 - lucen", Map.of("ok", true));
                }
                if (arguments.contains("document.body.innerText")) {
                    return new ChatToolExecutionResult("bash", "我的订阅\n套餐\n到期时间", Map.of("ok", true));
                }
                return new ChatToolExecutionResult("bash", "", Map.of("ok", true));
            });
            AtomicInteger modelRound = new AtomicInteger();
            doAnswer(invocation -> {
                AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
                int round = modelRound.incrementAndGet();
                if (round == 1) {
                    handler.onDelta("""
                        已确认目标页面 https://lucen.cc/subscriptions 当前已在用户浏览器中打开。
                        为确保内容可读，我将立即执行以下操作：
                        - Invoke-RestMethod -Uri "http://localhost:3456/info?target=0DD1D69470DAD7AF8466195E89014206"
                        - Invoke-WebRequest -Method Post -Body 'document.title' -Uri "http://localhost:3456/eval?target=0DD1D69470DAD7AF8466195E89014206"
                        - Invoke-WebRequest -Method Post -Body 'document.body.innerText.substring(0, 1000)' -Uri "http://localhost:3456/eval?target=0DD1D69470DAD7AF8466195E89014206"
                        正在执行……
                        """);
                    handler.onComplete();
                    return null;
                }
                handler.onDelta("已复用现有浏览器标签页，目标 URL 为 https://lucen.cc/subscriptions。");
                handler.onComplete();
                return null;
            }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

            chatApplicationService.sendMessage(
                new SendChatMessageCommand(14L, "连接订阅页", false, List.of(), List.of("web-access"), null, workspace.toString(), List.of()),
                1002L
            );

            verify(chatStreamPublisher, never()).publishAssistantDelta(
                eq(14L),
                org.mockito.ArgumentMatchers.contains("Invoke-RestMethod -Uri")
            );
            verify(chatToolExecutionService).execute(eq("bash"), org.mockito.ArgumentMatchers.contains("Invoke-RestMethod -Uri"));
            verify(chatToolExecutionService).execute(eq("bash"), org.mockito.ArgumentMatchers.contains("document.title"));
            verify(chatToolExecutionService).execute(eq("bash"), org.mockito.ArgumentMatchers.contains("document.body.innerText"));
            verify(chatStreamPublisher).publishAssistantDelta(
                14L,
                "已复用现有浏览器标签页，目标 URL 为 https://lucen.cc/subscriptions。"
            );
            verify(chatStreamPublisher).publishAssistantCompleted(
                eq(14L),
                any(),
                eq("已复用现有浏览器标签页，目标 URL 为 https://lucen.cc/subscriptions。"),
                eq("订阅页连接")
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
            assertEquals("AI 回复失败", failedMessage.getContent());
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
        verify(chatStreamPublisher).publishAssistantCompleted(
            eq(2L),
            any(),
            eq("已在 workspace 执行。"),
            eq("workspace 工具调用")
        );
        ChatExecutionContext.clear();
    }

    /**
     * 同一次聊天请求中的连续工具调用必须持续继承已绑定 skill 目录，避免第二次调用起丢失 CLAUDE_SKILL_DIR。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void sendMessageKeepsSkillDirectoriesAcrossMultipleToolCalls(@TempDir Path tempDir) throws Exception {
        Long runId = 9401008L;
        ChatExecutionContext.start(runId);
        Path skillDir = tempDir.resolve("codingx-skills").resolve("web-access");
        Files.createDirectories(skillDir);
        ChatConversation conversation = ChatConversation.create(8L, "Skill Tool Context", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(8L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(8L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(8L), eq(1002L))).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("连续调用技能工具", false, List.of("连续调用技能工具"))
        );
        when(conversationIntentService.route("连续调用技能工具", false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("web-access skill context");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("技能工具调用");
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec(
                "test_sync_tool",
                "同步测试工具",
                Map.of("type", "object", "properties", Map.of("message", Map.of("type", "string")))
            )
        ));
        List<Map<String, Path>> observedSkillDirectories = new ArrayList<>();
        when(chatToolExecutionService.execute(eq("test_sync_tool"), any())).thenAnswer(invocation -> {
            observedSkillDirectories.add(ChatToolExecutionContext.currentSkillDirectories());
            return new ChatToolExecutionResult("test_sync_tool", "技能工具已执行", Map.of("ok", true));
        });
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            if (modelRound.incrementAndGet() == 1) {
                handler.onToolCall(new AiToolCall("call-skill-1", "test_sync_tool", "{\"message\":\"first\"}"));
                handler.onToolCall(new AiToolCall("call-skill-2", "test_sync_tool", "{\"message\":\"second\"}"));
                handler.onComplete();
                return null;
            }
            handler.onDelta("技能工具调用完成。");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        ChatToolExecutionContext.bindSkillDirectories(Map.of("web-access", skillDir));
        try {
            chatApplicationService.sendMessage(
                new SendChatMessageCommand(8L, "连续调用技能工具", false, List.of(), List.of("web-access"), null, null, List.of()),
                1002L
            );
        } finally {
            ChatExecutionContext.clear();
            ChatToolExecutionContext.clear();
        }

        Map<String, Path> expectedSkillDirectories = Map.of("web-access", skillDir.toAbsolutePath().normalize());
        assertEquals(List.of(expectedSkillDirectories, expectedSkillDirectories), observedSkillDirectories);
        verify(chatStreamPublisher).publishAssistantCompleted(eq(8L), any(), eq("技能工具调用完成。"), eq("技能工具调用"));
    }

    /**
     * 技能编码只是上下文选择，不是模型可执行工具；模型伪造 web-access 工具调用时应忽略并继续生成回答。
     */
    @Test
    void sendMessageIgnoresSkillCodeToolCallWhenNotInVisibleToolSpecs() {
        Long runId = 9401009L;
        ChatExecutionContext.start(runId);
        ChatConversation conversation = ChatConversation.create(9L, "Skill Is Not Tool", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(9L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(9L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(9L), eq(1002L))).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("帮我打开小红书", false, List.of("帮我打开小红书"))
        );
        when(conversationIntentService.route("帮我打开小红书", false)).thenReturn(
            new ConversationIntentDecision("chat.normal", ConversationIntentAction.DIRECT, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("chat.normal")).thenReturn(null);
        when(chatSkillContextService.buildSkillContext(List.of("web-access"))).thenReturn("web-access skill context");
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("技能不是工具");
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatToolSpecService.listModelVisibleToolSpecs()).thenReturn(List.of(
            new ChatToolSpec("test_sync_tool", "同步测试工具", Map.of("type", "object"))
        ));
        AtomicInteger modelRound = new AtomicInteger();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<ChatMessage> history = invocation.getArgument(0);
            AiChatClient.ToolAwareStreamHandler handler = invocation.getArgument(3);
            if (modelRound.incrementAndGet() == 1) {
                handler.onToolCall(new AiToolCall("call-skill-as-tool", "web-access", "{\"query\":\"打开小红书\"}"));
                handler.onComplete();
                return null;
            }
            String invalidToolGuidance = history.stream()
                .filter(message -> message.getRole() == ChatMessageRole.SYSTEM)
                .map(ChatMessage::getContent)
                .filter(content -> content.contains("web-access") && content.contains("tool_call"))
                .reduce((first, second) -> second)
                .orElse("");
            assertTrue(invalidToolGuidance.contains("仅忽略这个错误的 tool_call"));
            assertTrue(invalidToolGuidance.contains("已选技能仍然有效"));
            assertTrue(invalidToolGuidance.contains("不要告诉用户"));
            assertFalse(invalidToolGuidance.contains("不可用"));
            assertFalse(invalidToolGuidance.contains("web-access 不可用"));
            assertFalse(invalidToolGuidance.contains("技能被忽略"));
            handler.onDelta("web-access 是已选技能，我会按技能说明继续处理。");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(9L, "帮我打开小红书", false, List.of(), List.of("web-access"), null, null, List.of()),
            1002L
        );

        verify(chatToolExecutionService, never()).execute(eq("web-access"), any());
        verify(chatStreamPublisher).publishAssistantCompleted(
            eq(9L),
            any(),
            eq("web-access 是已选技能，我会按技能说明继续处理。"),
            eq("技能不是工具")
        );
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
        assertEquals("AI 回复失败", failedMessage.getContent());
        assertEquals("先判断工具可用性。", failedMessage.getThinkingContent());
        ChatExecutionContext.clear();
    }
}
