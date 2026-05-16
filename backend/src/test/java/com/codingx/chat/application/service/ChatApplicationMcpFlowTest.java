package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
import com.codingx.chat.domain.service.AiChatClient;
import com.codingx.chat.domain.service.ChatStreamPublisher;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import java.util.ArrayList;
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

    @InjectMocks
    private ChatApplicationService chatApplicationService;

    /**
     * MCP 意图命中时应执行工具并直接返回工具结果。
     */
    @Test
    void sendMessageExecutesMcpToolForMcpIntent() {
        Long runId = 9301001L;
        ChatExecutionContext.start(runId);
        ChatConversation conversation = ChatConversation.create(1L, "New Conversation", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("销售总额是多少", false, java.util.List.of("销售总额是多少"))
        );
        when(conversationIntentService.route("销售总额是多少", true)).thenReturn(
            new ConversationIntentDecision("sales-data", ConversationIntentAction.MCP, null)
        );
        when(chatIntentNodeRepository.findByIntentCode("sales-data")).thenReturn(
            ChatIntentNode.builder().intentCode("sales-data").mcpToolId("sales_query").intentType("mcp").build()
        );
        when(chatMcpRepository.findByMcpCode("sales_query")).thenReturn(
            com.codingx.mcp.domain.model.ChatMcp.builder().mcpCode("sales_query").displayName("销售查询").enabled(1).build()
        );
        when(chatMcpExecutionService.execute("sales_query", "销售总额是多少")).thenReturn(
            new ChatMcpToolResult("sales_query", "销售总额为 1280 万元，本月环比增长 8%。", java.util.Map.of())
        );
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("销售数据统计");

        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "销售总额是多少", false, java.util.List.of("sales_query")), 1002L);

        verify(chatMcpExecutionService).execute("sales_query", "销售总额是多少");
        verify(chatStreamPublisher).publishAssistantCompleted(1L, "销售总额为 1280 万元，本月环比增长 8%。", "销售数据统计");
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals("销售总额为 1280 万元，本月环比增长 8%。", captor.getAllValues().get(1).getContent());
        org.mockito.Mockito.verifyNoInteractions(aiChatClient);
        ChatExecutionContext.clear();
    }
}
