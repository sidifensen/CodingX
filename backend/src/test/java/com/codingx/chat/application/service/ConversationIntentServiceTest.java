package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.codingx.chat.domain.model.ChatIntentNode;
import com.codingx.chat.domain.repository.ChatIntentNodeRepository;
import com.codingx.mcp.application.executor.WeatherQuestionParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
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
    private ConversationIntentResolver conversationIntentResolver;

    @Mock
    private ConversationIntentGuidanceService conversationIntentGuidanceService;

    @Spy
    private WeatherQuestionParser weatherQuestionParser = new WeatherQuestionParser();

    @InjectMocks
    private ConversationIntentService conversationIntentService;

    /**
     * 非系统知识类意图在当前项目中应进入 SEARCH 分支。
     */
    @Test
    void routeReturnsSearchActionForKnowledgeIntent() {
        ChatIntentNode node = ChatIntentNode.builder().intentCode("search-web-news").parentCode("search-web").name("新闻资讯").intentType("search").enabled(1).build();
        when(chatIntentNodeRepository.findEnabledNodes()).thenReturn(List.of(node));
        when(conversationIntentResolver.resolveCandidates("请介绍一下 OA 系统", List.of(node), List.of()))
            .thenReturn(List.of(new ConversationIntentCandidate(node, 0.91D)));
        when(conversationIntentGuidanceService.buildGuidancePrompt("请介绍一下 OA 系统", List.of(new ConversationIntentCandidate(node, 0.91D)), List.of(node)))
            .thenReturn(null);

        ConversationIntentDecision decision = conversationIntentService.route("请介绍一下 OA 系统");

        assertEquals(ConversationIntentAction.SEARCH, decision.action());
        assertEquals("search-web-news", decision.intentCode());
    }

    /**
     * SYSTEM 意图应进入 DIRECT 分支，而不是走搜索链路。
     */
    @Test
    void routeReturnsDirectActionForSystemIntent() {
        ChatIntentNode node = ChatIntentNode.builder().intentCode("sys-about-bot").name("关于助手").intentType("system").enabled(1).build();
        when(chatIntentNodeRepository.findEnabledNodes()).thenReturn(List.of(node));
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
        ChatIntentNode oa = ChatIntentNode.builder().intentCode("search-web-news").parentCode("search-web").name("系统介绍").intentType("search").enabled(1).build();
        ChatIntentNode ins = ChatIntentNode.builder().intentCode("search-web-encyclopedia").parentCode("search-web").name("系统介绍").intentType("search").enabled(1).build();
        List<ConversationIntentCandidate> candidates = List.of(
            new ConversationIntentCandidate(oa, 0.91D),
            new ConversationIntentCandidate(ins, 0.88D)
        );
        when(chatIntentNodeRepository.findEnabledNodes()).thenReturn(List.of(oa, ins));
        when(conversationIntentResolver.resolveCandidates("系统介绍是什么", List.of(oa, ins), List.of())).thenReturn(candidates);
        when(conversationIntentGuidanceService.buildGuidancePrompt("系统介绍是什么", candidates, List.of(oa, ins)))
            .thenReturn("关于系统介绍，候选如下：\n1) OA系统\n2) 保险系统");

        ConversationIntentDecision decision = conversationIntentService.route("系统介绍是什么");

        assertEquals(ConversationIntentAction.CLARIFY, decision.action());
        assertEquals("clarify.ambiguity", decision.intentCode());
        assertEquals("关于系统介绍，候选如下：\n1) OA系统\n2) 保险系统", decision.reply());
    }

    /**
     * 当前项目运行时应从节点 JSON 读取示例，避免再依赖已下线的示例表。
     */
    @Test
    void routeCollectsExamplesFromNodeExamplesOnly() {
        ChatIntentNode node = ChatIntentNode.builder()
            .intentCode("weather-data")
            .name("天气查询")
            .intentType("mcp")
            .enabled(1)
            .examples("[\"北京今天天气怎么样？\",\"上海明天会下雨吗？\"]")
            .build();
        when(chatIntentNodeRepository.findEnabledNodes()).thenReturn(List.of(node));
        when(conversationIntentResolver.resolveCandidates(eq("北京今天天气怎么样？"), eq(List.of(node)), any()))
            .thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                List<com.codingx.chat.domain.model.ChatIntentExample> examples = invocation.getArgument(2, List.class);
                List<String> exampleTexts = examples.stream().map(com.codingx.chat.domain.model.ChatIntentExample::getExampleText).toList();
                assertEquals(2, examples.size());
                assertTrue(exampleTexts.contains("北京今天天气怎么样？"));
                assertTrue(exampleTexts.contains("上海明天会下雨吗？"));
                return List.of(new ConversationIntentCandidate(node, 0.93D));
            });
        when(conversationIntentGuidanceService.buildGuidancePrompt(eq("北京今天天气怎么样？"), any(), eq(List.of(node))))
            .thenReturn(null);

        ConversationIntentDecision decision = conversationIntentService.route("北京今天天气怎么样？");

        assertEquals(ConversationIntentAction.MCP, decision.action());
        assertEquals("weather-data", decision.intentCode());
    }

    /**
     * 天气 MCP 已命中但缺少城市时，应先澄清必要参数，避免工具返回“请提供城市名称”后再交给模型硬答。
     */
    @Test
    void routeClarifiesWeatherMcpWhenCityIsMissing() {
        ChatIntentNode node = ChatIntentNode.builder()
            .intentCode("weather-data")
            .name("天气查询")
            .intentType("mcp")
            .mcpToolId("weather_query")
            .enabled(1)
            .examples("[\"北京今天天气怎么样？\",\"上海明天会下雨吗？\"]")
            .build();
        when(chatIntentNodeRepository.findEnabledNodes()).thenReturn(List.of(node));
        when(conversationIntentResolver.resolveCandidates(eq("天气怎么样"), eq(List.of(node)), any()))
            .thenReturn(List.of(new ConversationIntentCandidate(node, 0.92D)));
        when(conversationIntentGuidanceService.buildGuidancePrompt(eq("天气怎么样"), any(), eq(List.of(node))))
            .thenReturn(null);

        ConversationIntentDecision decision = conversationIntentService.route("天气怎么样", true);

        assertEquals(ConversationIntentAction.CLARIFY, decision.action());
        assertEquals("weather-data", decision.intentCode());
        assertEquals("请明确你想查询哪个城市的天气，例如：上海今天天气怎么样。", decision.reply());
    }

    /**
     * 天气槽位澄清必须在后端日志中打印原因和返回文案，方便排查指代词问题为何没有继续调用工具。
     */
    @Test
    void routeLogsClarificationReasonAndReplyWhenWeatherCityIsMissing() {
        ChatIntentNode node = ChatIntentNode.builder()
            .intentCode("weather-data")
            .name("天气查询")
            .intentType("mcp")
            .mcpToolId("weather_query")
            .enabled(1)
            .examples("[\"北京今天天气怎么样？\",\"上海明天会下雨吗？\"]")
            .build();
        List<ConversationIntentCandidate> candidates = List.of(new ConversationIntentCandidate(node, 0.92D));
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(ConversationIntentService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        when(chatIntentNodeRepository.findEnabledNodes()).thenReturn(List.of(node));
        when(conversationIntentResolver.resolveCandidates(eq("明天的天气怎么样"), eq(List.of(node)), any()))
            .thenReturn(candidates);
        when(conversationIntentGuidanceService.buildGuidancePrompt(eq("明天的天气怎么样"), eq(candidates), eq(List.of(node))))
            .thenReturn(null);

        try {
            ConversationIntentDecision decision = conversationIntentService.route("明天的天气怎么样", true);

            assertEquals(ConversationIntentAction.CLARIFY, decision.action());
            boolean logged = appender.list.stream()
                .anyMatch(event -> event.getLevel().equals(Level.INFO)
                    && event.getFormattedMessage().contains("澄清原因=天气缺少城市参数")
                    && event.getFormattedMessage().contains("澄清回复=请明确你想查询哪个城市的天气"));
            assertTrue(logged);
        } finally {
            logger.detachAppender(appender);
        }
    }

    /**
     * 天气问题已经包含城市时仍应正常进入 MCP，不能因新增槽位保护影响既有城市天气查询。
     */
    @Test
    void routeKeepsWeatherMcpWhenCityExists() {
        ChatIntentNode node = ChatIntentNode.builder()
            .intentCode("weather-data")
            .name("天气查询")
            .intentType("mcp")
            .mcpToolId("weather_query")
            .enabled(1)
            .examples("[\"北京今天天气怎么样？\",\"上海明天会下雨吗？\"]")
            .build();
        when(chatIntentNodeRepository.findEnabledNodes()).thenReturn(List.of(node));
        when(conversationIntentResolver.resolveCandidates(eq("上海今天天气怎么样"), eq(List.of(node)), any()))
            .thenReturn(List.of(new ConversationIntentCandidate(node, 0.94D)));
        when(conversationIntentGuidanceService.buildGuidancePrompt(eq("上海今天天气怎么样"), any(), eq(List.of(node))))
            .thenReturn(null);

        ConversationIntentDecision decision = conversationIntentService.route("上海今天天气怎么样", true);

        assertEquals(ConversationIntentAction.MCP, decision.action());
        assertEquals("weather-data", decision.intentCode());
        assertNull(decision.reply());
    }

}
