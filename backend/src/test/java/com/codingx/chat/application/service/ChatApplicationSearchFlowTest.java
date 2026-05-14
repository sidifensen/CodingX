package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.application.command.SendChatMessageCommand;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.chat.domain.service.AiChatClient;
import com.codingx.chat.domain.service.ChatStreamPublisher;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
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
    @Mock private AiChatClient aiChatClient;
    @Mock private ChatStreamPublisher chatStreamPublisher;
    @Mock private ChatRuntimeGuardService chatRuntimeGuardService;
    @Mock private ConversationTitleService conversationTitleService;
    @Mock private ConversationSummaryService conversationSummaryService;
    @Mock private ConversationRewriteService conversationRewriteService;
    @Mock private ConversationIntentService conversationIntentService;
    @Mock private WebSearchExecutionService webSearchExecutionService;
    @Mock private SearchReferenceCollector searchReferenceCollector;
    @Mock private DocumentArtifactService documentArtifactService;

    @InjectMocks
    private ChatApplicationService chatApplicationService;

    /**
     * 搜索型问题应触发搜索服务、参考来源收集和 docx 产物生成。
     */
    @Test
    void sendMessageInvokesSearchFlowForSearchIntent() {
        ChatConversation conversation = ChatConversation.create(1L, "New Conversation", 1002L, ChatConversationStatus.ACTIVE);
        when(chatConversationRepository.requireById(1L)).thenReturn(conversation);
        when(chatMessageRepository.findByConversationId(1L)).thenReturn(new ArrayList<>());
        when(conversationRewriteService.rewrite(any(), any())).thenReturn("请搜索 Spring Boot SSE");
        when(conversationIntentService.route("请搜索 Spring Boot SSE")).thenReturn(
            new ConversationIntentDecision("search.web", ConversationIntentAction.SEARCH, null)
        );
        when(webSearchExecutionService.search("请搜索 Spring Boot SSE")).thenReturn(List.of(
            new SearchReferenceCandidate("SSE", "https://example.com", "Example", "snippet")
        ));
        when(conversationTitleService.generateTitle(any(), any())).thenReturn("SSE搜索");
        org.mockito.Mockito.doAnswer(invocation -> {
            AiChatClient.StreamHandler handler = invocation.getArgument(1);
            handler.onDelta("搜索结果总结");
            handler.onComplete();
            return null;
        }).when(aiChatClient).streamChat(any(), any());

        chatApplicationService.sendMessage(new SendChatMessageCommand(1L, "请搜索 Spring Boot SSE"), 1002L);

        verify(webSearchExecutionService).search("请搜索 Spring Boot SSE");
        verify(searchReferenceCollector).collect(any(), any(), any(), any());
        verify(documentArtifactService).createDocxArtifact(any(), any(), any(), any());
    }
}
