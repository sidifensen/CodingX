package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.model.ChatIntentExample;
import com.codingx.chat.domain.model.ChatIntentNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证意图解析器会基于 Prompt 和候选叶子节点解析 LLM 返回结果。
 */
@ExtendWith(MockitoExtension.class)
class ConversationIntentResolverTest {

    @Mock
    private PromptTemplateLoader promptTemplateLoader;

    @Mock
    private AiPromptExecutionService aiPromptExecutionService;

    @InjectMocks
    private ConversationIntentResolver conversationIntentResolver;

    /**
     * 解析器应只使用叶子候选，并按 LLM 返回分数映射为排序结果。
     */
    @Test
    void resolveCandidatesMapsPromptResponseToSortedCandidates() {
        List<ChatIntentNode> nodes = List.of(
            ChatIntentNode.builder().intentCode("search-web").name("联网搜索").intentType("search").enabled(1).sortNo(1).build(),
            ChatIntentNode.builder().intentCode("search-web-oa").parentCode("search-web").name("OA系统").intentType("search").enabled(1).sortNo(2).build(),
            ChatIntentNode.builder().intentCode("search-web-oa-intro").parentCode("search-web-oa").name("系统介绍").intentType("search").enabled(1).sortNo(3).build(),
            ChatIntentNode.builder().intentCode("search.web").name("联网搜索兜底").intentType("search").enabled(1).sortNo(4).build()
        );
        List<ChatIntentExample> examples = List.of(
            ChatIntentExample.builder().intentCode("search-web-oa-intro").exampleText("OA系统是做什么的").sortNo(1).build()
        );
        when(promptTemplateLoader.render(anyString(), org.mockito.ArgumentMatchers.anyMap())).thenReturn("intent prompt");
        when(aiPromptExecutionService.complete("intent prompt", "请介绍一下 OA 系统")).thenReturn("""
            [
              {"id":"search-web-oa-intro","score":0.91,"reason":"问题直接询问 OA 系统介绍"},
              {"id":"search.web","score":0.42,"reason":"可通过搜索补充"}
            ]
            """);

        List<ConversationIntentCandidate> candidates = conversationIntentResolver.resolveCandidates("请介绍一下 OA 系统", nodes, examples);

        assertEquals(List.of("search-web-oa-intro", "search.web"), candidates.stream().map(candidate -> candidate.node().getIntentCode()).toList());
        assertEquals(0.91D, candidates.getFirst().score());
    }

    /**
     * 显式“联网搜索”问法应优先命中 SEARCH 节点，不再回落到 LLM 分类链路。
     */
    @Test
    void resolveCandidatesPrefersSearchHeuristicForExplicitWebSearchQuery() {
        List<ChatIntentNode> nodes = List.of(
            ChatIntentNode.builder().intentCode("search").name("联网搜索").intentType("search").enabled(1).sortNo(1).build(),
            ChatIntentNode.builder().intentCode("search-news").parentCode("search").name("新闻资讯").intentType("search").enabled(1).sortNo(2).build(),
            ChatIntentNode.builder().intentCode("search-facts").parentCode("search").name("事实查询").intentType("search").enabled(1).sortNo(3).build(),
            ChatIntentNode.builder().intentCode("search-general").parentCode("search").name("通用检索").intentType("search").enabled(1).sortNo(4).build(),
            ChatIntentNode.builder().intentCode("sys-about-bot").name("关于助手").intentType("system").enabled(1).sortNo(10).build()
        );

        List<ConversationIntentCandidate> candidates = conversationIntentResolver.resolveCandidates(
            "请联网搜索最新 Java 版本",
            nodes,
            List.of()
        );

        assertEquals("search-general", candidates.getFirst().node().getIntentCode());
        verifyNoInteractions(promptTemplateLoader, aiPromptExecutionService);
    }

    /**
     * 天气问法即使包含“最近/今天”等时效词，也应优先命中天气 MCP，而不是被通用搜索抢占。
     */
    @Test
    void resolveCandidatesPrefersWeatherMcpOverRecentSearchHeuristic() {
        List<ChatIntentNode> nodes = List.of(
            ChatIntentNode.builder().intentCode("search").name("联网搜索").intentType("search").enabled(1).sortNo(1).build(),
            ChatIntentNode.builder().intentCode("search-general").parentCode("search").name("通用检索").intentType("search").enabled(1).sortNo(2).build(),
            ChatIntentNode.builder().intentCode("weather").name("天气信息查询服务").intentType("mcp").enabled(1).sortNo(3).build(),
            ChatIntentNode.builder()
                .intentCode("weather-data")
                .parentCode("weather")
                .name("天气查询")
                .description("城市天气信息查询，覆盖今天、明天、未来预报、气温、温度、降雨、湿度、风力、空气质量等天气问法")
                .intentType("mcp")
                .mcpToolId("weather_query")
                .enabled(1)
                .sortNo(4)
                .build()
        );
        List<ChatIntentExample> examples = List.of(
            ChatIntentExample.builder().intentCode("weather-data").exampleText("今天北京的天气怎么样").sortNo(1).build(),
            ChatIntentExample.builder().intentCode("weather-data").exampleText("广州未来三天天气预报").sortNo(2).build()
        );

        List<ConversationIntentCandidate> candidates = conversationIntentResolver.resolveCandidates(
            "广州最近天气怎么样",
            nodes,
            examples
        );

        assertEquals("weather-data", candidates.getFirst().node().getIntentCode());
        verifyNoInteractions(promptTemplateLoader, aiPromptExecutionService);
    }
}
