package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatIntentNode;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.chat.domain.repository.ChatExecutionStepRepository;
import com.codingx.chat.domain.repository.ChatIntentNodeRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.chat.domain.port.AiChatClient;
import com.codingx.chat.domain.port.ChatStreamPublisher;
import com.codingx.mcp.application.service.ChatMcpExecutionService;
import com.codingx.mcp.application.executor.ChatMcpToolResult;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.expert.application.service.ChatExpertContextService;
import com.codingx.skill.application.service.ChatSkillContextService;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证 MCP 意图会走工具执行链路，而不是误入模型直答。
 */
@ExtendWith(MockitoExtension.class)
class ChatApplicationMcpFlowTest {

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
    @Mock private ChatMcpExecutionService chatMcpExecutionService;
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

    @InjectMocks
    private ChatApplicationService chatApplicationService;

    /**
     * MCP 意图命中时应执行工具并直接返回工具结果。
     */
    @Test
    void sendMessageExecutesMcpToolForMcpIntent() {
        Long runId = 9301001L;
        ChatExecutionContext.start(runId);
        AtomicBoolean aiStreamInvoked = new AtomicBoolean(false);
        ChatConversation conversation = ChatConversation.create(1L, "New Conversation", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(java.util.List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("北京今天天气怎么样", false, java.util.List.of("北京今天天气怎么样"))
        );
        when(conversationIntentService.route("北京今天天气怎么样", true)).thenReturn(
            new ConversationIntentDecision("weather-data", ConversationIntentAction.MCP, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("weather-data")).thenReturn(
            ChatIntentNode.builder().intentCode("weather-data").mcpToolId("weather_query").intentType("mcp").build()
        );
        when(chatMcpRepository.findByMcpCode("weather_query")).thenReturn(
            com.codingx.mcp.domain.model.ChatMcp.builder().mcpCode("weather_query").displayName("天气查询").enabled(1).build()
        );
        when(chatMcpExecutionService.execute(
            eq("weather_query"),
            eq("北京今天天气怎么样"),
            any(com.codingx.mcp.application.executor.ChatMcpProgressListener.class)
        )).thenAnswer(invocation -> {
            com.codingx.mcp.application.executor.ChatMcpProgressListener listener = invocation.getArgument(2);
            listener.onProgress("mock-progress", "正在查询天气数据", java.util.Map.of("source", "mock"));
            return new ChatMcpToolResult("weather_query", "北京今日晴，当前温度 26.5°C。", java.util.Map.of());
        });
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("天气查询");
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doAnswer(invocation -> {
            aiStreamInvoked.set(true);
            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onMetadata("mock-provider", "mock-model");
            handler.onThinkingDelta("正在基于工具结果整理最终答案。");
            handler.onDelta("根据天气工具结果，北京今日晴，当前温度 26.5°C。");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), eq(false), any(AiChatClient.StreamHandler.class));

        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "北京今天天气怎么样", false, java.util.List.of("weather_query")), 1002L);

        verify(chatMcpExecutionService).execute(
            eq("weather_query"),
            eq("北京今天天气怎么样"),
            any(com.codingx.mcp.application.executor.ChatMcpProgressListener.class)
        );
        // 新契约：MCP 调用需先上报 start，再上报 complete，且两次都携带 callId。
        verify(chatStreamPublisher, atLeast(1)).publishMcpCall(eq(1L), org.mockito.ArgumentMatchers.argThat(payload -> {
            if (!(payload instanceof Map<?, ?> map)) {
                return false;
            }
            return "start".equals(map.get("phase"))
                && map.get("callId") != null
                && map.containsKey("params");
        }));
        verify(chatStreamPublisher, atLeast(1)).publishMcpCall(eq(1L), org.mockito.ArgumentMatchers.argThat(payload -> {
            if (!(payload instanceof Map<?, ?> map)) {
                return false;
            }
            return "progress".equals(map.get("phase"))
                && map.get("callId") != null
                && map.containsKey("progressText");
        }));
        verify(chatStreamPublisher, atLeast(1)).publishMcpCall(eq(1L), org.mockito.ArgumentMatchers.argThat(payload -> {
            if (!(payload instanceof Map<?, ?> map)) {
                return false;
            }
            return "complete".equals(map.get("phase"))
                && map.get("callId") != null
                && map.containsKey("rawResult");
        }));
        verify(aiChatClient).streamChat(any(), eq(false), any(AiChatClient.StreamHandler.class));
        assertEquals(true, aiStreamInvoked.get());
        verify(chatStreamPublisher).publishAssistantCompleted(eq(1L), any(Long.class), eq("根据天气工具结果，北京今日晴，当前温度 26.5°C。"), eq("天气查询"));
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals("根据天气工具结果，北京今日晴，当前温度 26.5°C。", captor.getAllValues().get(1).getContent());
        ChatExecutionContext.clear();
    }

    /**
     * 命中代码检索意图时应调用 code_search 工具并透传结果。
     */
    @Test
    void sendMessageExecutesCodeSearchToolForCodeIntent() {
        Long runId = 9301002L;
        ChatExecutionContext.start(runId);
        ChatConversation conversation = ChatConversation.create(2L, "Code Search Conversation", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(2L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(2L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(2L), eq(1002L))).thenReturn(java.util.List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("查找 ChatController 的 sendMessage 方法", false, java.util.List.of("查找 ChatController 的 sendMessage 方法"))
        );
        when(conversationIntentService.route("查找 ChatController 的 sendMessage 方法", true)).thenReturn(
            new ConversationIntentDecision("code-search", ConversationIntentAction.MCP, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("code-search")).thenReturn(
            ChatIntentNode.builder().intentCode("code-search").mcpToolId("code_search").intentType("mcp").build()
        );
        when(chatMcpRepository.findByMcpCode("code_search")).thenReturn(
            com.codingx.mcp.domain.model.ChatMcp.builder().mcpCode("code_search").displayName("代码检索").enabled(1).build()
        );
        when(chatMcpExecutionService.execute(
            eq("code_search"),
            eq("查找 ChatController 的 sendMessage 方法"),
            any(com.codingx.mcp.application.executor.ChatMcpProgressListener.class)
        )).thenReturn(new ChatMcpToolResult("code_search", "命中 ChatController.java:95", java.util.Map.of()));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("代码定位");
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doAnswer(invocation -> {
            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onMetadata("mock-provider", "mock-model");
            handler.onThinkingDelta("正在根据代码检索结果整理最终答案。");
            handler.onDelta("根据代码检索结果，命中 ChatController.java:95。");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), eq(false), any(AiChatClient.StreamHandler.class));

        chatApplicationService.sendMessage(
            new SendChatMessageCommand(2L, "查找 ChatController 的 sendMessage 方法", false, java.util.List.of("code_search")),
            1002L
        );

        verify(chatMcpExecutionService).execute(
            eq("code_search"),
            eq("查找 ChatController 的 sendMessage 方法"),
            any(com.codingx.mcp.application.executor.ChatMcpProgressListener.class)
        );
        verify(aiChatClient).streamChat(any(), eq(false), any(AiChatClient.StreamHandler.class));
        verify(chatStreamPublisher).publishAssistantCompleted(eq(2L), any(Long.class), eq("根据代码检索结果，命中 ChatController.java:95。"), eq("代码定位"));
        ChatExecutionContext.clear();
    }
}


