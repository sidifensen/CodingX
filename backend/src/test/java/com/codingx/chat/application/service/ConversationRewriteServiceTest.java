package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证查询改写服务会通过 Prompt 驱动改写结果，而不是只拼接字符串。
 */
@ExtendWith(MockitoExtension.class)
class ConversationRewriteServiceTest {

    @Mock
    private PromptTemplateLoader promptTemplateLoader;

    @Mock
    private AiPromptExecutionService aiPromptExecutionService;

    @Mock
    private ConversationQueryTermMappingService conversationQueryTermMappingService;

    @InjectMocks
    private ConversationRewriteService conversationRewriteService;

    /**
     * 改写服务应解析模型返回的 JSON，并提取 rewrite 字段。
     */
    @Test
    void rewriteUsesPromptDrivenJsonResult() {
        when(conversationQueryTermMappingService.normalize("这个要怎么改")).thenReturn("CodingX 聊天架构怎么改");
        when(promptTemplateLoader.load("rewrite")).thenReturn("rewrite prompt");
        when(aiPromptExecutionService.complete(
            "rewrite prompt",
            "历史上下文：上一轮问的是 CodingX 聊天架构\n当前问题：CodingX 聊天架构怎么改"
        )).thenReturn("""
            {
              "rewrite":"CodingX 聊天架构应该怎么改",
              "should_split":false,
              "sub_questions":["CodingX 聊天架构应该怎么改"]
            }
            """);

        String rewritten = conversationRewriteService.rewrite(List.of("上一轮问的是 CodingX 聊天架构"), "这个要怎么改");

        assertEquals("CodingX 聊天架构应该怎么改", rewritten);
    }

    /**
     * 当模型返回多子问题时，应完整保留拆分结果，供主链路后续并行执行。
     */
    @Test
    void rewriteResultKeepsSplitQuestionsFromPromptResponse() {
        when(conversationQueryTermMappingService.normalize("帮我分别介绍 OA 系统和保险系统")).thenReturn("帮我分别介绍 OA 系统和保险系统");
        when(promptTemplateLoader.load("rewrite")).thenReturn("rewrite prompt");
        when(aiPromptExecutionService.complete(
            "rewrite prompt",
            "历史上下文：无\n当前问题：帮我分别介绍 OA 系统和保险系统"
        )).thenReturn("""
            {
              "rewrite":"介绍 OA 系统和保险系统",
              "should_split":true,
              "sub_questions":["介绍 OA 系统","介绍 保险系统"]
            }
            """);

        ConversationRewriteResult result = conversationRewriteService.rewriteResult(List.of(), "帮我分别介绍 OA 系统和保险系统");

        assertEquals(true, result.shouldSplit());
        assertEquals(List.of("介绍 OA 系统", "介绍 保险系统"), result.subQuestions());
    }

    /**
     * 销售统计类短问题在无上下文时应跳过 LLM 改写，直接保留原问法。
     */
    @Test
    void rewriteResultBypassesPromptForSalesQuestionWithoutHistory() {
        when(conversationQueryTermMappingService.normalize("销售总额是多少")).thenReturn("销售总额是多少");

        ConversationRewriteResult result = conversationRewriteService.rewriteResult(List.of(), "销售总额是多少");

        assertEquals("销售总额是多少", result.rewrite());
        assertEquals(List.of("销售总额是多少"), result.subQuestions());
    }

    /**
     * 第一轮自包含问题没有历史指代，也不需要拆分时，应直接跳过改写模型，减少回答前的额外等待。
     */
    @Test
    void rewriteResultBypassesPromptForSelfContainedQuestionWithoutHistory() {
        when(conversationQueryTermMappingService.normalize("请解释一下 Java Stream 的作用")).thenReturn("请解释一下 Java Stream 的作用");

        ConversationRewriteResult result = conversationRewriteService.rewriteResult(
            List.of("请解释一下 Java Stream 的作用"),
            "请解释一下 Java Stream 的作用"
        );

        assertEquals("请解释一下 Java Stream 的作用", result.rewrite());
        assertEquals(false, result.shouldSplit());
        assertEquals(List.of("请解释一下 Java Stream 的作用"), result.subQuestions());
        verifyNoInteractions(promptTemplateLoader, aiPromptExecutionService);
    }

    /**
     * 会话已有旧问题时，如果本轮问题仍是完整独立问题，也应跳过改写模型，避免长会话里每轮都额外等待。
     */
    @Test
    void rewriteResultBypassesPromptForSelfContainedQuestionWithUnrelatedHistory() {
        when(conversationQueryTermMappingService.normalize("请解释一下 Java Stream 的作用")).thenReturn("请解释一下 Java Stream 的作用");

        ConversationRewriteResult result = conversationRewriteService.rewriteResult(
            List.of("上一轮问的是 CodingX 聊天架构", "请解释一下 Java Stream 的作用"),
            "请解释一下 Java Stream 的作用"
        );

        assertEquals("请解释一下 Java Stream 的作用", result.rewrite());
        assertEquals(false, result.shouldSplit());
        assertEquals(List.of("请解释一下 Java Stream 的作用"), result.subQuestions());
        verifyNoInteractions(promptTemplateLoader, aiPromptExecutionService);
    }

    /**
     * 改写服务不应对特定厂商或产品追加写死的搜索锚点，搜索质量由通用排序链路处理。
     */
    @Test
    void rewriteResultDoesNotAppendVendorSpecificSearchHint() {
        when(conversationQueryTermMappingService.normalize("AcmeDB 最新版本是什么")).thenReturn("AcmeDB 最新版本是什么");
        when(promptTemplateLoader.load("rewrite")).thenReturn("rewrite prompt");
        when(aiPromptExecutionService.complete(
            "rewrite prompt",
            "历史上下文：无\n当前问题：AcmeDB 最新版本是什么"
        )).thenReturn("""
            {
              "rewrite":"AcmeDB 最新版本是什么",
              "should_split":false,
              "sub_questions":["AcmeDB 最新版本是什么"]
            }
            """);

        ConversationRewriteResult result = conversationRewriteService.rewriteResult(List.of(), "AcmeDB 最新版本是什么");

        assertEquals("AcmeDB 最新版本是什么", result.rewrite());
        assertEquals(List.of("AcmeDB 最新版本是什么"), result.subQuestions());
    }

    /**
     * 改写服务应兼容模型把 JSON 包在 markdown 代码块中的返回格式，避免把有效结果误判为异常。
     */
    @Test
    void rewriteResultStripsMarkdownCodeFenceBeforeParsingJson() {
        when(conversationQueryTermMappingService.normalize("你好")).thenReturn("你好");
        when(promptTemplateLoader.load("rewrite")).thenReturn("rewrite prompt");
        when(aiPromptExecutionService.complete(
            "rewrite prompt",
            "历史上下文：无\n当前问题：你好"
        )).thenReturn("""
            ```json
            {
              "rewrite":"你好",
              "should_split":false,
              "sub_questions":["你好"]
            }
            ```
            """);

        ConversationRewriteResult result = conversationRewriteService.rewriteResult(List.of(), "你好");

        assertEquals("你好", result.rewrite());
        assertEquals(false, result.shouldSplit());
        assertEquals(List.of("你好"), result.subQuestions());
    }

    /**
     * 当模型未按约定返回 JSON 时，改写服务应降级为规范化后的原问题，而不是把异常继续扩散到主链路。
     */
    @Test
    void rewriteResultFallsBackToNormalizedQuestionWhenModelReturnsPlainText() {
        when(conversationQueryTermMappingService.normalize("你好")).thenReturn("你好");
        when(promptTemplateLoader.load("rewrite")).thenReturn("rewrite prompt");
        when(aiPromptExecutionService.complete(
            "rewrite prompt",
            "历史上下文：无\n当前问题：你好"
        )).thenReturn("你好");

        ConversationRewriteResult result = conversationRewriteService.rewriteResult(List.of(), "你好");

        assertEquals("你好", result.rewrite());
        assertEquals(false, result.shouldSplit());
        assertEquals(List.of("你好"), result.subQuestions());
    }
}
