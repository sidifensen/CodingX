package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.model.ChatIntentExample;
import com.codingx.chat.domain.model.ChatIntentNode;
import com.codingx.chat.domain.repository.ChatIntentExampleRepository;
import com.codingx.chat.domain.repository.ChatIntentNodeRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证聊天意图服务对三类问题的最小分流。
 */
@ExtendWith(MockitoExtension.class)
class ConversationIntentServiceTest {

    /**
     * 节点仓储依赖。
     */
    @Mock
    private ChatIntentNodeRepository chatIntentNodeRepository;

    /**
     * 示例仓储依赖。
     */
    @Mock
    private ChatIntentExampleRepository chatIntentExampleRepository;

    /**
     * 解析器依赖。
     */
    @Mock
    private ConversationIntentResolver conversationIntentResolver;

    /**
     * 被测服务。
     */
    @InjectMocks
    private ConversationIntentService conversationIntentService;

    /**
     * 搜索类问题应进入 SEARCH 分支。
     */
    @Test
    void routeReturnsSearchActionForSearchIntent() {
        when(chatIntentNodeRepository.findEnabledNodes()).thenReturn(List.of(
            ChatIntentNode.builder().intentCode("search.web").intentType("search").enabled(1).build()
        ));
        when(chatIntentExampleRepository.findByIntentCode("search.web")).thenReturn(List.of());
        when(conversationIntentResolver.resolveIntent(org.mockito.ArgumentMatchers.eq("请搜索 Spring Boot SSE"), anyList(), anyList())).thenReturn("search.web");

        ConversationIntentDecision decision = conversationIntentService.route("请搜索 Spring Boot SSE");

        assertEquals(ConversationIntentAction.SEARCH, decision.action());
        assertEquals("search.web", decision.intentCode());
    }

    /**
     * 模糊问题应直接进入澄清分支。
     */
    @Test
    void routeReturnsClarifyActionForAmbiguousQuestion() {
        ConversationIntentDecision decision = conversationIntentService.route("这个要怎么改");

        assertEquals(ConversationIntentAction.CLARIFY, decision.action());
        assertEquals("clarify.ambiguity", decision.intentCode());
    }

    /**
     * 普通问题应走 DIRECT 分支。
     */
    @Test
    void routeReturnsDirectActionForNormalQuestion() {
        when(chatIntentNodeRepository.findEnabledNodes()).thenReturn(List.of(
            ChatIntentNode.builder().intentCode("chat.normal").intentType("chat").enabled(1).build()
        ));
        when(chatIntentExampleRepository.findByIntentCode("chat.normal")).thenReturn(List.of());
        when(conversationIntentResolver.resolveIntent(org.mockito.ArgumentMatchers.eq("帮我总结一下这个项目"), anyList(), anyList())).thenReturn("chat.normal");

        ConversationIntentDecision decision = conversationIntentService.route("帮我总结一下这个项目");

        assertEquals(ConversationIntentAction.DIRECT, decision.action());
        assertEquals("chat.normal", decision.intentCode());
    }
}
