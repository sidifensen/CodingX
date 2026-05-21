package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

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

    /**
     * 同名主题跨系统命中且分数接近时应生成澄清文案。
     */
    @Test
    void buildGuidancePromptReturnsPromptForAmbiguousCandidates() throws Exception {
        java.nio.file.Path promptDir = java.nio.file.Files.createTempDirectory("codingx-guidance");
        java.nio.file.Files.writeString(promptDir.resolve("guidance-prompt.st"), "关于{topic_name}，候选如下：\n{options}");
        java.nio.file.Files.writeString(promptDir.resolve("guidance-ambiguity-check.st"), "check");
        ConversationIntentGuidanceService service = new ConversationIntentGuidanceService(new PromptTemplateLoader(promptDir), aiPromptExecutionService);
        ChatIntentNode oa = ChatIntentNode.builder().intentCode("search-web-oa-intro").parentCode("search-web-oa").name("系统介绍").intentType("search").build();
        ChatIntentNode ins = ChatIntentNode.builder().intentCode("search-web-ins-intro").parentCode("search-web-ins").name("系统介绍").intentType("search").build();

        String prompt = service.buildGuidancePrompt(
            "系统介绍是什么",
            List.of(
                new ConversationIntentCandidate(oa, 0.75D),
                new ConversationIntentCandidate(ins, 0.67D)
            ),
            List.of(
                ChatIntentNode.builder().intentCode("search-web-oa").parentCode("search-web").name("OA系统").intentType("search").build(),
                ChatIntentNode.builder().intentCode("search-web-ins").parentCode("search-web").name("保险系统").intentType("search").build(),
                ChatIntentNode.builder().intentCode("search-web").name("联网搜索").intentType("search").build()
            )
        );

        assertEquals("关于系统介绍，候选如下：\n1) 联网搜索 > OA系统 > 系统介绍\n2) 联网搜索 > 保险系统 > 系统介绍", prompt);
    }

    /**
     * 主题明确落在单一候选时不应返回澄清文案。
     */
    @Test
    void buildGuidancePromptReturnsNullWhenQuestionIsClear() throws Exception {
        java.nio.file.Path promptDir = java.nio.file.Files.createTempDirectory("codingx-guidance");
        java.nio.file.Files.writeString(promptDir.resolve("guidance-prompt.st"), "关于{topic_name}，候选如下：\n{options}");
        java.nio.file.Files.writeString(promptDir.resolve("guidance-ambiguity-check.st"), "check");
        ConversationIntentGuidanceService service = new ConversationIntentGuidanceService(new PromptTemplateLoader(promptDir), aiPromptExecutionService);
        ChatIntentNode node = ChatIntentNode.builder().intentCode("search-web-it").parentCode("search-web").name("IT支持").intentType("search").build();

        String prompt = service.buildGuidancePrompt("VPN 连不上怎么办", List.of(new ConversationIntentCandidate(node, 0.92D)), List.of());

        assertNull(prompt);
    }
}
