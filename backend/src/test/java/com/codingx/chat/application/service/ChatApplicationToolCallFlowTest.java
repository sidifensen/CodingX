package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.command.SendChatMessageCommand;
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
            handler.onToolCall(new AiToolCall("call-failed", "spawn_agent", "{\"message\":\"x\"}"));
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChatWithTools(any(), eq(false), any(), any());

        assertDoesNotThrow(() -> chatApplicationService.sendMessage(
            new SendChatMessageCommand(3L, "调用不可用工具", false, List.of(), List.of(), null, null, List.of()),
            1002L
        ));

        verify(chatStreamPublisher).publishError(3L, "spawn_agent 暂未接入真实 Codex 运行时");
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(messageCaptor.capture());
        ChatMessage failedMessage = messageCaptor.getAllValues().get(1);
        assertEquals(ChatMessageStatus.FAILED, failedMessage.getStatus());
        assertEquals("AI 回复失败", failedMessage.getContent());
        ChatExecutionContext.clear();
    }
}
