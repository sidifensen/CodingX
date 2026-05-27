package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
     * GPT 最新模型类问题应补充 OpenAI 官方文档限定词，减少搜索引擎返回旧版本或第三方传言的概率。
     */
    @Test
    void rewriteResultAddsOfficialOpenAiHintForLatestGptModelQuestion() {
        when(conversationQueryTermMappingService.normalize("gpt最新模型是什么")).thenReturn("gpt最新模型是什么");
        when(promptTemplateLoader.load("rewrite")).thenReturn("rewrite prompt");
        when(aiPromptExecutionService.complete(
            "rewrite prompt",
            "历史上下文：无\n当前问题：gpt最新模型是什么 OpenAI 官方文档 latest model developers.openai.com"
        )).thenReturn("""
            {
              "rewrite":"gpt最新模型是什么 OpenAI 官方文档 latest model developers.openai.com",
              "should_split":false,
              "sub_questions":["gpt最新模型是什么 OpenAI 官方文档 latest model developers.openai.com"]
            }
            """);

        ConversationRewriteResult result = conversationRewriteService.rewriteResult(List.of(), "gpt最新模型是什么");

        assertEquals("gpt最新模型是什么 OpenAI 官方文档 latest model developers.openai.com", result.rewrite());
        assertEquals(
            List.of("gpt最新模型是什么 OpenAI 官方文档 latest model developers.openai.com"),
            result.subQuestions()
        );
    }
}
