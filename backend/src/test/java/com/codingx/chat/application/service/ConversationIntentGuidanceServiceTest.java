package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.codingx.chat.domain.model.ChatIntentNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证歧义澄清服务会在跨系统同名主题且分数接近时返回澄清提示。
 */
class ConversationIntentGuidanceServiceTest {

    /**
     * 同名主题跨系统命中且分数接近时应生成澄清文案。
     */
    @Test
    void buildGuidancePromptReturnsPromptForAmbiguousCandidates() throws Exception {
        java.nio.file.Path promptDir = java.nio.file.Files.createTempDirectory("codingx-guidance");
        java.nio.file.Files.writeString(promptDir.resolve("guidance-prompt.st"), "关于{topic_name}，候选如下：\n{options}");
        ConversationIntentGuidanceService service = new ConversationIntentGuidanceService(new PromptTemplateLoader(promptDir));
        ChatIntentNode oa = ChatIntentNode.builder().intentCode("biz-oa-intro").parentCode("biz-oa").name("系统介绍").intentType("kb").build();
        ChatIntentNode ins = ChatIntentNode.builder().intentCode("biz-ins-intro").parentCode("biz-ins").name("系统介绍").intentType("kb").build();

        String prompt = service.buildGuidancePrompt(
            "系统介绍是什么",
            List.of(
                new ConversationIntentCandidate(oa, 0.91D),
                new ConversationIntentCandidate(ins, 0.87D)
            ),
            List.of(
                ChatIntentNode.builder().intentCode("biz-oa").parentCode("biz").name("OA系统").intentType("kb").build(),
                ChatIntentNode.builder().intentCode("biz-ins").parentCode("biz").name("保险系统").intentType("kb").build(),
                ChatIntentNode.builder().intentCode("biz").name("业务系统").intentType("kb").build()
            )
        );

        assertEquals("关于系统介绍，候选如下：\n1) 业务系统 > OA系统 > 系统介绍\n2) 业务系统 > 保险系统 > 系统介绍", prompt);
    }

    /**
     * 主题明确落在单一候选时不应返回澄清文案。
     */
    @Test
    void buildGuidancePromptReturnsNullWhenQuestionIsClear() throws Exception {
        java.nio.file.Path promptDir = java.nio.file.Files.createTempDirectory("codingx-guidance");
        java.nio.file.Files.writeString(promptDir.resolve("guidance-prompt.st"), "关于{topic_name}，候选如下：\n{options}");
        ConversationIntentGuidanceService service = new ConversationIntentGuidanceService(new PromptTemplateLoader(promptDir));
        ChatIntentNode node = ChatIntentNode.builder().intentCode("group-it").parentCode("group").name("IT支持").intentType("kb").build();

        String prompt = service.buildGuidancePrompt("VPN 连不上怎么办", List.of(new ConversationIntentCandidate(node, 0.92D)), List.of());

        assertNull(prompt);
    }
}
