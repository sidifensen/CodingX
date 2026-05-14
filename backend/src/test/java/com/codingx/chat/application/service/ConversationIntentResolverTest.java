package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.codingx.chat.domain.model.ChatIntentExample;
import com.codingx.chat.domain.model.ChatIntentNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证意图解析器的最小分流能力。
 */
class ConversationIntentResolverTest {

    /**
     * 包含搜索语义的问题应优先命中 search 意图。
     */
    @Test
    void resolveReturnsSearchIntentForSearchLikeQuestion() {
        ConversationIntentResolver resolver = new ConversationIntentResolver();
        List<ChatIntentNode> nodes = List.of(
            ChatIntentNode.builder().intentCode("chat.normal").name("普通闲聊").intentType("chat").enabled(1).sortNo(1).build(),
            ChatIntentNode.builder().intentCode("search.web").name("联网搜索").intentType("search").enabled(1).sortNo(2).build()
        );
        List<ChatIntentExample> examples = List.of(
            ChatIntentExample.builder().intentCode("search.web").exampleText("帮我搜索一下最新资料").sortNo(1).build()
        );

        String intentCode = resolver.resolveIntent("请搜索 Spring Boot SSE 最佳实践", nodes, examples);

        assertEquals("search.web", intentCode);
    }
}
