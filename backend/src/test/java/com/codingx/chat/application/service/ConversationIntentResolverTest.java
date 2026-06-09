package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.model.ChatIntentExample;
import com.codingx.chat.domain.model.ChatIntentNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
     * 兼容模型把 JSON 包在 markdown 代码块中的常见输出格式，避免有效分类结果被解析失败吞掉。
     */
    @Test
    void resolveCandidatesStripsMarkdownCodeFenceBeforeParsingModelResponse() {
        List<ChatIntentNode> nodes = List.of(
            ChatIntentNode.builder().intentCode("external").name("外部服务").intentType("mcp").enabled(1).sortNo(1).build(),
            ChatIntentNode.builder()
                .intentCode("external-service")
                .parentCode("external")
                .name("第三方接口")
                .description("外部接口调用")
                .intentType("mcp")
                .enabled(1)
                .sortNo(2)
                .build()
        );
        when(promptTemplateLoader.render(anyString(), anyMap())).thenReturn("intent prompt");
        when(aiPromptExecutionService.complete("intent prompt", "北京天气")).thenReturn("""
            ```json
            [{"id":"external-service","score":0.93,"reason":"模型选择该外部接口"}]
            ```
            """);

        List<ConversationIntentCandidate> candidates = conversationIntentResolver.resolveCandidates("北京天气", nodes, List.of());

        assertEquals("external-service", candidates.getFirst().node().getIntentCode());
    }

    /**
     * 配置示例已能精确命中的系统问题应直接返回本地高置信候选，避免回答前再等待意图分类模型。
     */
    @Test
    void resolveCandidatesBypassesPromptForExactConfiguredExample() {
        List<ChatIntentNode> nodes = List.of(
            ChatIntentNode.builder().intentCode("sys").name("系统意图").intentType("system").enabled(1).sortNo(1).build(),
            ChatIntentNode.builder().intentCode("sys-about-bot").parentCode("sys").name("关于助手").intentType("system").enabled(1).sortNo(2).build()
        );
        List<ChatIntentExample> examples = List.of(
            ChatIntentExample.builder().intentCode("sys-about-bot").exampleText("你是谁").sortNo(1).build()
        );

        List<ConversationIntentCandidate> candidates = conversationIntentResolver.resolveCandidates("你是谁", nodes, examples);

        assertEquals("sys-about-bot", candidates.getFirst().node().getIntentCode());
        assertEquals(0.96D, candidates.getFirst().score());
        verifyNoInteractions(promptTemplateLoader, aiPromptExecutionService);
    }

    /**
     * 无配置候选的普通自包含直答问题应直接回落 chat.normal，不再等待意图分类模型。
     */
    @Test
    void resolveCandidatesBypassesPromptForPlainDirectQuestionWithoutConfiguredMatch() {
        List<ChatIntentNode> nodes = List.of(
            ChatIntentNode.builder().intentCode("search").name("联网搜索").intentType("search").enabled(1).sortNo(1).build(),
            ChatIntentNode.builder().intentCode("search-general").parentCode("search").name("通用检索").description("开放网页检索").intentType("search").enabled(1).sortNo(2).build(),
            ChatIntentNode.builder().intentCode("weather").name("天气服务").intentType("mcp").enabled(1).sortNo(3).build(),
            ChatIntentNode.builder().intentCode("weather-data").parentCode("weather").name("天气查询").description("城市天气查询").intentType("mcp").mcpToolId("weather_query").enabled(1).sortNo(4).build()
        );

        List<ConversationIntentCandidate> candidates = conversationIntentResolver.resolveCandidates("请解释一下 Java Stream 的作用", nodes, List.of());

        assertEquals(List.of(), candidates);
        verifyNoInteractions(promptTemplateLoader, aiPromptExecutionService);
    }

    /**
     * 显式“联网搜索”问法应由配置化候选和模型分数决定，不在 Java 中维护搜索关键词表。
     */
    @Test
    void resolveCandidatesUsesModelScoreForExplicitWebSearchQuery() {
        List<ChatIntentNode> nodes = List.of(
            ChatIntentNode.builder().intentCode("search").name("联网搜索").intentType("search").enabled(1).sortNo(1).build(),
            ChatIntentNode.builder().intentCode("search-news").parentCode("search").name("新闻资讯").intentType("search").enabled(1).sortNo(2).build(),
            ChatIntentNode.builder().intentCode("search-facts").parentCode("search").name("事实查询").intentType("search").enabled(1).sortNo(3).build(),
            ChatIntentNode.builder().intentCode("search-general").parentCode("search").name("通用检索").intentType("search").enabled(1).sortNo(4).build(),
            ChatIntentNode.builder().intentCode("sys-about-bot").name("关于助手").intentType("system").enabled(1).sortNo(10).build()
        );
        when(promptTemplateLoader.render(anyString(), anyMap())).thenReturn("intent prompt");
        when(aiPromptExecutionService.complete("intent prompt", "请联网搜索最新 Java 版本")).thenReturn("""
            [{"id":"search-general","score":0.96,"reason":"问题明确要求联网检索最新版本信息"}]
            """);

        List<ConversationIntentCandidate> candidates = conversationIntentResolver.resolveCandidates(
            "请联网搜索最新 Java 版本",
            nodes,
            List.of()
        );

        assertEquals("search-general", candidates.getFirst().node().getIntentCode());
        verify(promptTemplateLoader).render(anyString(), anyMap());
        verify(aiPromptExecutionService).complete("intent prompt", "请联网搜索最新 Java 版本");
    }

    /**
     * 天气问法由天气 MCP 节点自身的描述和示例交给模型判断，不依赖写死的天气关键词保护逻辑。
     */
    @Test
    void resolveCandidatesUsesModelScoreForWeatherMcpQuery() {
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
        when(promptTemplateLoader.render(anyString(), anyMap())).thenReturn("intent prompt");
        when(aiPromptExecutionService.complete("intent prompt", "广州最近天气怎么样")).thenReturn("""
            [{"id":"weather-data","score":0.97,"reason":"问题询问城市天气，应调用天气 MCP"}]
            """);

        List<ConversationIntentCandidate> candidates = conversationIntentResolver.resolveCandidates(
            "广州最近天气怎么样",
            nodes,
            examples
        );

        assertEquals("weather-data", candidates.getFirst().node().getIntentCode());
        ArgumentCaptor<java.util.Map<String, String>> variablesCaptor = ArgumentCaptor.captor();
        verify(promptTemplateLoader).render(anyString(), variablesCaptor.capture());
        String intentList = (String) variablesCaptor.getValue().get("intent_list");
        assertTrue(intentList.contains("type=MCP"));
        assertTrue(intentList.contains("toolId=weather_query"));
        verify(aiPromptExecutionService).complete("intent prompt", "广州最近天气怎么样");
    }

    /**
     * 模型不可用时才进入本地兜底；兜底只能读取节点名称、路径、描述和示例这类配置化语义。
     */
    @Test
    void resolveCandidatesFallsBackToConfiguredNodeTextWhenModelUnavailable() {
        List<ChatIntentNode> nodes = List.of(
            ChatIntentNode.builder().intentCode("search").name("联网搜索").intentType("search").enabled(1).sortNo(1).build(),
            ChatIntentNode.builder()
                .intentCode("search-general")
                .parentCode("search")
                .name("通用检索")
                .description("联网资料检索，用于查询公开网页信息")
                .intentType("search")
                .enabled(1)
                .sortNo(2)
                .build(),
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
        when(promptTemplateLoader.render(anyString(), anyMap())).thenReturn("intent prompt");
        when(aiPromptExecutionService.complete("intent prompt", "广州最近天气怎么样")).thenThrow(new IllegalStateException("llm unavailable"));

        List<ConversationIntentCandidate> candidates = conversationIntentResolver.resolveCandidates(
            "广州最近天气怎么样",
            nodes,
            examples
        );

        assertEquals("weather-data", candidates.getFirst().node().getIntentCode());
        verify(promptTemplateLoader).render(anyString(), anyMap());
        verify(aiPromptExecutionService).complete("intent prompt", "广州最近天气怎么样");
    }

    /**
     * 当模型返回普通文本而不是 JSON 时，应直接走本地兜底候选，避免 JSON 解析异常污染主链路日志。
     */
    @Test
    void resolveCandidatesFallsBackWhenModelReturnsPlainTextInsteadOfJson() {
        List<ChatIntentNode> nodes = List.of(
            ChatIntentNode.builder().intentCode("system").name("系统问候").intentType("system").enabled(1).sortNo(1).build(),
            ChatIntentNode.builder()
                .intentCode("sys-welcome")
                .parentCode("system")
                .name("欢迎语")
                .description("你好、hello、hi 等问候语")
                .intentType("system")
                .enabled(1)
                .sortNo(2)
                .build()
        );
        List<ChatIntentExample> examples = List.of(
            ChatIntentExample.builder().intentCode("sys-welcome").exampleText("你好").sortNo(1).build()
        );
        when(promptTemplateLoader.render(anyString(), anyMap())).thenReturn("intent prompt");
        when(aiPromptExecutionService.complete("intent prompt", "你好呀")).thenReturn("你好呀");

        List<ConversationIntentCandidate> candidates = conversationIntentResolver.resolveCandidates("你好呀", nodes, examples);

        assertEquals("sys-welcome", candidates.getFirst().node().getIntentCode());
        verify(promptTemplateLoader).render(anyString(), anyMap());
        verify(aiPromptExecutionService).complete("intent prompt", "你好呀");
    }
}
