package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证查询改写服务的最小行为。
 */
class ConversationRewriteServiceTest {

    /**
     * 有历史上下文时应把最近问题与当前问题拼出更稳定的检索问法。
     */
    @Test
    void rewriteIncludesLastUserContextWhenPresent() {
        ConversationRewriteService service = new ConversationRewriteService();

        String rewritten = service.rewrite(List.of("上一轮问的是 CodingX 聊天架构"), "这个要怎么改");

        assertEquals("结合上下文“上一轮问的是 CodingX 聊天架构”，当前问题是：这个要怎么改", rewritten);
    }
}
