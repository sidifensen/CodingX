package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证聊天消息正文中的能力标记解析规则，保证落库可读与模型输入可分离。
 */
class ChatCapabilityMentionSupportTest {

    /**
     * 选中技能时应把技能编码写成消息前缀，并保持用户正文可剥离还原。
     */
    @Test
    void formatSkillMentionsAndStripPlainContent() {
        String persistedContent = ChatCapabilityMentionSupport.formatContentWithSkillMentions(
            List.of("web-access", "web-access", "frontend-design-3.0"),
            "这是啥"
        );

        assertEquals("@web-access @frontend-design-3.0 这是啥", persistedContent);
        assertEquals(List.of("web-access", "frontend-design-3.0"), ChatCapabilityMentionSupport.parseSkillCodes(persistedContent));
        assertEquals("这是啥", ChatCapabilityMentionSupport.stripLeadingMentions(persistedContent));
    }

    /**
     * 仅输入技能前缀时应保持单个技能标记，避免格式化时把同一技能重复写入 content。
     */
    @Test
    void formatSkillOnlyContentWithoutDuplicateMention() {
        String persistedContent = ChatCapabilityMentionSupport.formatContentWithSkillMentions(
            List.of("web-access"),
            "@web-access"
        );

        assertEquals("@web-access", persistedContent);
        assertEquals("", ChatCapabilityMentionSupport.stripLeadingMentions(persistedContent));
    }

    /**
     * 用户可能同时通过技能选择器和正文尾部输入同一个 @skill。
     * 业务意图：尾部重复技能标记仍是能力选择，不应残留给模型触发自由发挥。
     */
    @Test
    void formatSkillMentionsStripsDuplicateInlineMentionFromPlainQuestion() {
        String persistedContent = ChatCapabilityMentionSupport.formatContentWithSkillMentions(
            List.of("multi-search"),
            "这是什么 @multi-search"
        );

        assertEquals("@multi-search 这是什么", persistedContent);
        assertEquals("这是什么", ChatCapabilityMentionSupport.stripLeadingMentions(persistedContent));
    }

    /**
     * 清理正文能力标记时只能删除已选技能，未选中的 @ 文本仍是用户真实输入。
     */
    @Test
    void stripSelectedSkillMentionsPreservesUnselectedInlineMention() {
        String plainContent = ChatCapabilityMentionSupport.stripSelectedSkillMentions(
            "比较 @multi-search 和 @other-skill",
            List.of("multi-search")
        );

        assertEquals("比较 和 @other-skill", plainContent);
    }
}
