package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.codingx.chat.domain.model.ChatIntentNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证歧义澄清服务会在跨系统同名主题且分数接近时返回澄清提示。
 */
@ExtendWith(MockitoExtension.class)
class ConversationIntentGuidanceServiceTest {

    @Mock
    private AiPromptExecutionService aiPromptExecutionService;

    @Mock
    private RuntimeSettingService runtimeSettingService;

    /**
     * 同名主题跨系统命中且分数接近时应生成澄清文案。
     */
    @Test
    void buildGuidancePromptReturnsPromptForAmbiguousCandidates() throws Exception {
        ChatIntentNode oa = ChatIntentNode.builder().intentCode("search-web-oa-intro").parentCode("search-web-oa").name("系统介绍").intentType("search").build();
        ChatIntentNode ins = ChatIntentNode.builder().intentCode("search-web-ins-intro").parentCode("search-web-ins").name("系统介绍").intentType("search").build();
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(ConversationIntentAmbiguityDetector.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            String prompt = buildService().buildGuidancePrompt(
                "系统介绍是什么",
                List.of(
                    new ConversationIntentCandidate(oa, 0.90D),
                    new ConversationIntentCandidate(ins, 0.82D)
                ),
                defaultNodes()
            );

            assertEquals("关于系统介绍，候选如下：\n1) 联网搜索 > OA系统 > 系统介绍\n2) 联网搜索 > 保险系统 > 系统介绍", prompt);
            verify(aiPromptExecutionService, never()).complete(anyString(), anyString());
            boolean logged = appender.list.stream()
                .anyMatch(event -> event.getLevel().equals(Level.INFO) && event.getFormattedMessage().contains("歧义引导触发"));
            assertEquals(true, logged);
        } finally {
            logger.detachAppender(appender);
        }
    }

    /**
     * 主题明确落在单一候选时不应返回澄清文案。
     */
    @Test
    void buildGuidancePromptReturnsNullWhenQuestionIsClear() throws Exception {
        ChatIntentNode node = ChatIntentNode.builder().intentCode("search-web-it").parentCode("search-web").name("IT支持").intentType("search").build();

        String prompt = buildService().buildGuidancePrompt("VPN 连不上怎么办", List.of(new ConversationIntentCandidate(node, 0.92D)), List.of());

        assertNull(prompt);
    }

    /**
     * 管理端关闭歧义引导后，后端必须短路，不再继续做 LLM 复核。
     */
    @Test
    void buildGuidancePromptReturnsNullWhenGuidanceDisabled() throws Exception {
        when(runtimeSettingService.chatIntentGuidanceEnabled()).thenReturn(false);

        String prompt = buildServiceWithoutDefaultSettings().buildGuidancePrompt(
            "系统介绍是什么",
            List.of(
                new ConversationIntentCandidate(node("search-web-oa-intro", "search-web-oa", "系统介绍"), 0.90D),
                new ConversationIntentCandidate(node("search-web-ins-intro", "search-web-ins", "系统介绍"), 0.88D)
            ),
            defaultNodes()
        );

        assertNull(prompt);
        verify(aiPromptExecutionService, never()).complete(anyString(), anyString());
    }

    /**
     * 比值落入边界区间时沿用 ragent 的 LLM 二次确认策略。
     */
    @Test
    void buildGuidancePromptUsesLlmCheckerForBoundaryRatio() throws Exception {
        when(aiPromptExecutionService.complete(anyString(), anyString())).thenReturn("{\"ambiguous\":true}");

        String prompt = buildService().buildGuidancePrompt(
            "系统介绍是什么",
            List.of(
                new ConversationIntentCandidate(node("search-web-oa-intro", "search-web-oa", "系统介绍"), 0.90D),
                new ConversationIntentCandidate(node("search-web-ins-intro", "search-web-ins", "系统介绍"), 0.68D)
            ),
            defaultNodes()
        );

        assertEquals("关于系统介绍，候选如下：\n1) 联网搜索 > OA系统 > 系统介绍\n2) 联网搜索 > 保险系统 > 系统介绍", prompt);
        verify(aiPromptExecutionService).complete(anyString(), anyString());
    }

    /**
     * 边界区间 LLM 明确判断无歧义时，不应继续提示用户选择。
     */
    @Test
    void buildGuidancePromptReturnsNullWhenBoundaryLlmRejectsAmbiguity() throws Exception {
        when(aiPromptExecutionService.complete(anyString(), anyString())).thenReturn("{\"ambiguous\":false}");

        String prompt = buildService().buildGuidancePrompt(
            "系统介绍是什么",
            List.of(
                new ConversationIntentCandidate(node("search-web-oa-intro", "search-web-oa", "系统介绍"), 0.90D),
                new ConversationIntentCandidate(node("search-web-ins-intro", "search-web-ins", "系统介绍"), 0.68D)
            ),
            defaultNodes()
        );

        assertNull(prompt);
    }

    /**
     * 用户问题已经写明系统名时，歧义引导不能再次询问相同范围。
     */
    @Test
    void buildGuidancePromptReturnsNullWhenQuestionContainsSystemName() throws Exception {
        String prompt = buildService().buildGuidancePrompt(
            "OA系统的系统介绍是什么",
            List.of(
                new ConversationIntentCandidate(node("search-web-oa-intro", "search-web-oa", "系统介绍"), 0.90D),
                new ConversationIntentCandidate(node("search-web-ins-intro", "search-web-ins", "系统介绍"), 0.88D)
            ),
            defaultNodes()
        );

        assertNull(prompt);
        verify(aiPromptExecutionService, never()).complete(anyString(), anyString());
    }

    /**
     * 同一系统只展示最高分候选，并按配置限制最多展示的候选数量。
     */
    @Test
    void buildGuidancePromptDeduplicatesSystemsAndTrimsOptions() throws Exception {
        stubDefaultGuidanceSettings(2);

        String prompt = buildServiceWithoutDefaultSettings().buildGuidancePrompt(
            "系统介绍是什么",
            List.of(
                new ConversationIntentCandidate(node("search-web-oa-intro", "search-web-oa", "系统介绍"), 0.92D),
                new ConversationIntentCandidate(node("search-web-oa-security", "search-web-oa", "安全说明"), 0.91D),
                new ConversationIntentCandidate(node("search-web-ins-intro", "search-web-ins", "系统介绍"), 0.88D),
                new ConversationIntentCandidate(node("search-web-fin-intro", "search-web-fin", "系统介绍"), 0.87D)
            ),
            List.of(
                ChatIntentNode.builder().intentCode("search-web").name("联网搜索").intentType("search").build(),
                ChatIntentNode.builder().intentCode("search-web-oa").parentCode("search-web").name("OA系统").intentType("search").build(),
                ChatIntentNode.builder().intentCode("search-web-ins").parentCode("search-web").name("保险系统").intentType("search").build(),
                ChatIntentNode.builder().intentCode("search-web-fin").parentCode("search-web").name("财务系统").intentType("search").build()
            )
        );

        assertEquals("关于系统介绍，候选如下：\n1) 联网搜索 > OA系统 > 系统介绍\n2) 联网搜索 > 保险系统 > 系统介绍", prompt);
    }

    /**
     * ragent 会先过滤低置信候选，避免两个很低分候选仅因比值接近而误触发澄清。
     */
    @Test
    void buildGuidancePromptReturnsNullForLowConfidenceCandidates() throws Exception {
        String prompt = buildService().buildGuidancePrompt(
            "随便聊聊",
            List.of(
                new ConversationIntentCandidate(node("search-web-oa-intro", "search-web-oa", "系统介绍"), 0.20D),
                new ConversationIntentCandidate(node("search-web-ins-intro", "search-web-ins", "系统介绍"), 0.19D)
            ),
            defaultNodes()
        );

        assertNull(prompt);
        verify(aiPromptExecutionService, never()).complete(anyString(), anyString());
    }

    /**
     * 歧义引导只处理同名主题跨系统的场景，不能把同一大类下的不同搜索策略都拿来询问用户。
     */
    @Test
    void buildGuidancePromptReturnsNullForDifferentSearchTopicsUnderSameDomain() throws Exception {
        String prompt = buildService().buildGuidancePrompt(
            "GPT 的最新模型是什么",
            List.of(
                new ConversationIntentCandidate(node("search-web-general", "search-web", "通用检索"), 0.91D),
                new ConversationIntentCandidate(node("search-web-fact", "search-web", "事实查询"), 0.89D),
                new ConversationIntentCandidate(node("search-web-news", "search-web", "新闻资讯"), 0.86D)
            ),
            defaultNodes()
        );

        assertNull(prompt);
        verify(aiPromptExecutionService, never()).complete(anyString(), anyString());
    }

    /**
     * 边界区间的 LLM 复核需要兼容模型返回 Markdown code fence 包裹的 JSON。
     */
    @Test
    void buildGuidancePromptParsesFencedJsonWhenBoundaryLlmRejectsAmbiguity() throws Exception {
        when(aiPromptExecutionService.complete(anyString(), anyString())).thenReturn("""
            ```json
            {"ambiguous":false,"category_ids":["search-web-oa-intro"],"reason":"用户已经更偏向 OA"}
            ```
            """);

        String prompt = buildService().buildGuidancePrompt(
            "系统介绍是什么",
            List.of(
                new ConversationIntentCandidate(node("search-web-oa-intro", "search-web-oa", "系统介绍"), 0.90D),
                new ConversationIntentCandidate(node("search-web-ins-intro", "search-web-ins", "系统介绍"), 0.68D)
            ),
            defaultNodes()
        );

        assertNull(prompt);
    }

    private ConversationIntentGuidanceService buildService() throws Exception {
        stubDefaultGuidanceSettings(6);
        return buildServiceWithoutDefaultSettings();
    }

    private void stubDefaultGuidanceSettings(int maxOptions) {
        lenient().when(runtimeSettingService.chatIntentGuidanceEnabled()).thenReturn(true);
        lenient().when(runtimeSettingService.chatIntentGuidanceAmbiguityScoreRatio()).thenReturn(0.8D);
        lenient().when(runtimeSettingService.chatIntentGuidanceAmbiguityMargin()).thenReturn(0.15D);
        lenient().when(runtimeSettingService.chatIntentGuidanceMaxOptions()).thenReturn(maxOptions);
    }

    private ConversationIntentGuidanceService buildServiceWithoutDefaultSettings() throws Exception {
        java.nio.file.Path promptDir = java.nio.file.Files.createTempDirectory("codingx-guidance");
        java.nio.file.Files.writeString(promptDir.resolve("guidance-prompt.st"), "关于{topic_name}，候选如下：\n{options}");
        java.nio.file.Files.writeString(promptDir.resolve("guidance-ambiguity-check.st"), "check {question} {candidates}");
        PromptTemplateLoader loader = new PromptTemplateLoader(promptDir);
        ConversationIntentPathResolver pathResolver = new ConversationIntentPathResolver();
        ConversationIntentAmbiguityDetector detector = new ConversationIntentAmbiguityDetector(
            runtimeSettingService,
            loader,
            aiPromptExecutionService,
            pathResolver
        );
        return new ConversationIntentGuidanceService(loader, detector, pathResolver);
    }

    private List<ChatIntentNode> defaultNodes() {
        return List.of(
            ChatIntentNode.builder().intentCode("search-web").name("联网搜索").intentType("search").build(),
            ChatIntentNode.builder().intentCode("search-web-oa").parentCode("search-web").name("OA系统").intentType("search").build(),
            ChatIntentNode.builder().intentCode("search-web-ins").parentCode("search-web").name("保险系统").intentType("search").build()
        );
    }

    private ChatIntentNode node(String intentCode, String parentCode, String name) {
        return ChatIntentNode.builder()
            .intentCode(intentCode)
            .parentCode(parentCode)
            .name(name)
            .intentType("search")
            .build();
    }
}
