package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatExecutionRunRepository;
import com.codingx.chat.domain.repository.ChatExecutionStepRepository;
import com.codingx.chat.domain.repository.ChatIntentNodeRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.chat.domain.service.AiChatClient;
import com.codingx.chat.domain.service.ChatStreamPublisher;
import com.codingx.expert.application.service.ChatExpertContextService;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.skill.application.service.ChatSkillContextService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证搜索型问题会进入搜索链路并落来源/产物。
 */
@ExtendWith(MockitoExtension.class)
class ChatApplicationSearchFlowTest {

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
    @Mock private com.codingx.common.support.ai.TokenCounterService tokenCounterService;
    @Mock private com.codingx.common.support.ai.LlmResponseCleaner llmResponseCleaner;
    @Mock private ChatMcpRepository chatMcpRepository;
    @Mock private ChatAttachmentService chatAttachmentService;
    @Mock private ChatSkillContextService chatSkillContextService;
    @Mock private ChatExpertContextService chatExpertContextService;
    @Mock private RuntimeSettingService runtimeSettingService;
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();

    @InjectMocks
    private ChatApplicationService chatApplicationService;

    @AfterEach
    void shutdownExecutor() {
        searchExecutor.shutdownNow();
    }

    /**
     * 搜索型问题应触发搜索服务、参考来源收集和 docx 产物生成。
     */
    @Test
    void sendMessageInvokesSearchFlowForSearchIntent() {
        Long runId = 9201001L;
        ChatExecutionContext.start(runId);
        ChatConversation conversation = ChatConversation.create(1L, "New Conversation", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("请搜索 Spring Boot SSE", false, List.of("请搜索 Spring Boot SSE"))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(runtimeSettingService.searchMaxParallelQuestions()).thenReturn(3);
        when(conversationIntentService.route("请搜索 Spring Boot SSE", false)).thenReturn(
            new ConversationIntentDecision("search.web", ConversationIntentAction.SEARCH, null)
        );
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatIntentNodeRepository.findByIntentCode("search.web")).thenReturn(null);
        when(webSearchExecutionService.search("请搜索 Spring Boot SSE")).thenReturn(List.of(
            new SearchReferenceCandidate("SSE", "https://example.com", "Example", "snippet")
        ));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("SSE搜索");
        org.mockito.Mockito.doAnswer(invocation -> {
            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onDelta("搜索结果总结");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), org.mockito.ArgumentMatchers.anyBoolean(), any());

        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "请搜索 Spring Boot SSE", false), 1002L);

        verify(webSearchExecutionService).search("请搜索 Spring Boot SSE");
        verify(searchReferenceCollector).collect(any(), any(), any(), any());
        verify(documentArtifactService).createDocxArtifact(any(), any(), any(), any());
        ChatExecutionContext.clear();
    }

    /**
     * 改写结果包含多个子问题时，应对每个子问题分别执行搜索。
     */
    @Test
    void sendMessageExecutesSearchForEachSplitQuestion() {
        Long runId = 9201002L;
        ChatExecutionContext.start(runId);
        ChatConversation conversation = ChatConversation.create(1L, "New Conversation", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(chatAttachmentService.requireOwnedAttachments(any(), eq(1L), eq(1002L))).thenReturn(List.of());
        when(conversationRewriteService.rewriteResult(any(), any())).thenReturn(
            new ConversationRewriteResult("介绍 OA 系统和保险系统", true, List.of("介绍 OA 系统", "介绍 保险系统"))
        );
        when(conversationSummaryService.buildModelHistory(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(runtimeSettingService.searchMaxParallelQuestions()).thenReturn(3);
        when(conversationIntentService.route("介绍 OA 系统和保险系统", false)).thenReturn(
            new ConversationIntentDecision("biz-oa-intro", ConversationIntentAction.SEARCH, null)
        );
        when(chatExpertContextService.buildExpertContext(any())).thenReturn("");
        when(chatSkillContextService.buildSkillContext(any())).thenReturn("");
        when(chatIntentNodeRepository.findByIntentCode("biz-oa-intro")).thenReturn(null);
        when(webSearchExecutionService.search("介绍 OA 系统")).thenReturn(List.of(
            new SearchReferenceCandidate("OA系统", "https://example.com/oa", "Example", "oa")
        ));
        when(webSearchExecutionService.search("介绍 保险系统")).thenReturn(List.of(
            new SearchReferenceCandidate("保险系统", "https://example.com/ins", "Example", "ins")
        ));
        when(llmResponseCleaner.clean(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("双系统介绍");
        org.mockito.Mockito.doAnswer(invocation -> {
            AiChatClient.StreamHandler handler = invocation.getArgument(2);
            handler.onDelta("合并总结");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), org.mockito.ArgumentMatchers.anyBoolean(), any());

        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "帮我分别介绍 OA 系统和保险系统", false), 1002L);

        verify(webSearchExecutionService).search("介绍 OA 系统");
        verify(webSearchExecutionService).search("介绍 保险系统");
        verify(searchReferenceCollector).collect(any(), any(), any(), any());
        ChatExecutionContext.clear();
    }
}
