package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
 * 验证聊天意图服务会基于候选节点、系统意图和歧义澄清输出正确动作。
 */
@ExtendWith(MockitoExtension.class)
class ConversationIntentServiceTest {

    @Mock
    private ChatIntentNodeRepository chatIntentNodeRepository;

    @Mock
    private ChatIntentExampleRepository chatIntentExampleRepository;

    @Mock
    private ConversationIntentResolver conversationIntentResolver;

    @Mock
    private ConversationIntentGuidanceService conversationIntentGuidanceService;

    @InjectMocks
    private ConversationIntentService conversationIntentService;

    /**
     * 非系统知识类意图在当前项目中应进入 SEARCH 分支。
     */
    @Test
    void routeReturnsSearchActionForKnowledgeIntent() {
        ChatIntentNode node = ChatIntentNode.builder().intentCode("biz-oa-intro").parentCode("biz-oa").name("系统介绍").intentType("kb").enabled(1).build();
        when(chatIntentNodeRepository.findEnabledNodes()).thenReturn(List.of(node));
        when(chatIntentExampleRepository.findByIntentCode("biz-oa-intro")).thenReturn(List.of());
        when(conversationIntentResolver.resolveCandidates("请介绍一下 OA 系统", List.of(node), List.of()))
            .thenReturn(List.of(new ConversationIntentCandidate(node, 0.91D)));
        when(conversationIntentGuidanceService.buildGuidancePrompt("请介绍一下 OA 系统", List.of(new ConversationIntentCandidate(node, 0.91D)), List.of(node)))
            .thenReturn(null);

        ConversationIntentDecision decision = conversationIntentService.route("请介绍一下 OA 系统");

        assertEquals(ConversationIntentAction.SEARCH, decision.action());
        assertEquals("biz-oa-intro", decision.intentCode());
    }

    /**
     * SYSTEM 意图应进入 DIRECT 分支，而不是走搜索链路。
     */
    @Test
    void routeReturnsDirectActionForSystemIntent() {
        ChatIntentNode node = ChatIntentNode.builder().intentCode("sys-about-bot").name("关于助手").intentType("system").enabled(1).build();
        when(chatIntentNodeRepository.findEnabledNodes()).thenReturn(List.of(node));
        when(chatIntentExampleRepository.findByIntentCode("sys-about-bot")).thenReturn(List.of());
        when(conversationIntentResolver.resolveCandidates("你是谁", List.of(node), List.of()))
            .thenReturn(List.of(new ConversationIntentCandidate(node, 0.95D)));
        when(conversationIntentGuidanceService.buildGuidancePrompt("你是谁", List.of(new ConversationIntentCandidate(node, 0.95D)), List.of(node)))
            .thenReturn(null);

        ConversationIntentDecision decision = conversationIntentService.route("你是谁");

        assertEquals(ConversationIntentAction.DIRECT, decision.action());
        assertEquals("sys-about-bot", decision.intentCode());
        assertNull(decision.reply());
    }

    /**
     * 跨系统同名主题分数接近时，应返回澄清提示。
     */
    @Test
    void routeReturnsClarifyActionWhenGuidancePromptExists() {
        ChatIntentNode oa = ChatIntentNode.builder().intentCode("biz-oa-intro").parentCode("biz-oa").name("系统介绍").intentType("kb").enabled(1).build();
        ChatIntentNode ins = ChatIntentNode.builder().intentCode("biz-ins-intro").parentCode("biz-ins").name("系统介绍").intentType("kb").enabled(1).build();
        List<ConversationIntentCandidate> candidates = List.of(
            new ConversationIntentCandidate(oa, 0.91D),
            new ConversationIntentCandidate(ins, 0.88D)
        );
        when(chatIntentNodeRepository.findEnabledNodes()).thenReturn(List.of(oa, ins));
        when(chatIntentExampleRepository.findByIntentCode(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());
        when(conversationIntentResolver.resolveCandidates("系统介绍是什么", List.of(oa, ins), List.of())).thenReturn(candidates);
        when(conversationIntentGuidanceService.buildGuidancePrompt("系统介绍是什么", candidates, List.of(oa, ins)))
            .thenReturn("关于系统介绍，候选如下：\n1) OA系统\n2) 保险系统");

        ConversationIntentDecision decision = conversationIntentService.route("系统介绍是什么");

        assertEquals(ConversationIntentAction.CLARIFY, decision.action());
        assertEquals("clarify.ambiguity", decision.intentCode());
        assertEquals("关于系统介绍，候选如下：\n1) OA系统\n2) 保险系统", decision.reply());
    }

    /**
     * 当前项目运行时应同时吸收节点 JSON 示例与旧示例表，确保导入 ragent SQL 后无需双写也能参与识别。
     */
    @Test
    void routeMergesNodeExamplesWithLegacyExamples() {
        ChatIntentNode node = ChatIntentNode.builder()
            .intentCode("weather-data")
            .name("天气查询")
            .intentType("mcp")
            .enabled(1)
            .examples("[\"北京今天天气怎么样？\",\"上海明天会下雨吗？\"]")
            .build();
        when(chatIntentNodeRepository.findEnabledNodes()).thenReturn(List.of(node));
        when(chatIntentExampleRepository.findByIntentCode("weather-data")).thenReturn(List.of(
            ChatIntentExample.builder().intentCode("weather-data").exampleText("杭州现在多少度？").sortNo(1).build()
        ));
        when(conversationIntentResolver.resolveCandidates(eq("北京今天天气怎么样？"), eq(List.of(node)), any()))
            .thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                List<ChatIntentExample> examples = invocation.getArgument(2, List.class);
                List<String> exampleTexts = examples.stream().map(ChatIntentExample::getExampleText).toList();
                assertEquals(3, examples.size());
                assertTrue(exampleTexts.contains("北京今天天气怎么样？"));
                assertTrue(exampleTexts.contains("上海明天会下雨吗？"));
                assertTrue(exampleTexts.contains("杭州现在多少度？"));
                return List.of(new ConversationIntentCandidate(node, 0.93D));
            });
        when(conversationIntentGuidanceService.buildGuidancePrompt(eq("北京今天天气怎么样？"), any(), eq(List.of(node))))
            .thenReturn(null);

        ConversationIntentDecision decision = conversationIntentService.route("北京今天天气怎么样？");

        assertEquals(ConversationIntentAction.MCP, decision.action());
        assertEquals("weather-data", decision.intentCode());
    }
}
