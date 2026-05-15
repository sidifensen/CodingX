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
}
