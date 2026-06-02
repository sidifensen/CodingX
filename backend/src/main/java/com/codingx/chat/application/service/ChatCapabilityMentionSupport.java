package com.codingx.chat.application.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一处理聊天正文中的能力标记，保证数据库可读标记与模型自然语言输入互不污染。
 */
public final class ChatCapabilityMentionSupport {

    private static final Pattern LEADING_MENTIONS_PATTERN = Pattern.compile("^\\s*((?:@[A-Za-z0-9_.:-]+\\s*)+)");
    private static final Pattern MENTION_TOKEN_PATTERN = Pattern.compile("@([A-Za-z0-9_.:-]+)");
    private static final Pattern STANDALONE_MENTION_TOKEN_PATTERN = Pattern.compile(
        "(?<![A-Za-z0-9_.:-])@([A-Za-z0-9_.:-]+)(?=$|\\s|[\\p{Punct}，。？！、：；“”‘’（）【】《》])"
    );

    private ChatCapabilityMentionSupport() {
    }

    /**
     * 将选中技能写入用户消息正文前缀；已有前缀会先剥离，避免重复落库。
     * @param skillCodes 选中技能编码。
     * @param content 用户原始正文。
     * @return 可持久化的正文。
     */
    public static String formatContentWithSkillMentions(List<String> skillCodes, String content) {
        List<String> normalizedSkillCodes = normalizeCodes(skillCodes);
        if (normalizedSkillCodes.isEmpty()) {
            return StrUtil.trimToEmpty(content);
        }
        String plainContent = stripSelectedSkillMentions(content, normalizedSkillCodes);
        String mentions = normalizedSkillCodes.stream()
            .map(skillCode -> "@" + skillCode)
            .collect(java.util.stream.Collectors.joining(" "));
        return StrUtil.isBlank(plainContent) ? mentions : mentions + " " + plainContent;
    }

    /**
     * 从正文前缀解析技能编码，非前缀位置的 @ 文本不会被当作技能。
     * @param content 消息正文。
     * @return 按出现顺序去重后的技能编码。
     */
    public static List<String> parseSkillCodes(String content) {
        if (StrUtil.isBlank(content)) {
            return List.of();
        }
        Matcher prefixMatcher = LEADING_MENTIONS_PATTERN.matcher(content);
        if (!prefixMatcher.find()) {
            return List.of();
        }
        LinkedHashSet<String> skillCodes = new LinkedHashSet<>();
        Matcher tokenMatcher = MENTION_TOKEN_PATTERN.matcher(prefixMatcher.group(1));
        while (tokenMatcher.find()) {
            String skillCode = StrUtil.trimToEmpty(tokenMatcher.group(1));
            if (StrUtil.isNotBlank(skillCode)) {
                skillCodes.add(skillCode);
            }
        }
        return List.copyOf(skillCodes);
    }

    /**
     * 剥离正文开头的技能标记，供改写、意图识别、模型历史和标题生成使用。
     * @param content 消息正文。
     * @return 不含开头技能标记的正文。
     */
    public static String stripLeadingMentions(String content) {
        if (StrUtil.isBlank(content)) {
            return "";
        }
        Matcher matcher = LEADING_MENTIONS_PATTERN.matcher(content);
        if (!matcher.find()) {
            return content.trim();
        }
        return content.substring(matcher.end()).trim();
    }

    /**
     * 剥离正文中属于本轮已选技能的能力标记，避免 UI token 残留污染意图识别和模型输入。
     * 关键约束：只删除已选技能对应的独立 @skill token，未选中的 @ 文本仍作为用户正文保留。
     * @param content 消息正文。
     * @param selectedSkillCodes 本轮已选技能编码。
     * @return 不含已选技能标记的自然语言正文。
     */
    public static String stripSelectedSkillMentions(String content, List<String> selectedSkillCodes) {
        String plainContent = stripLeadingMentions(content);
        List<String> normalizedSkillCodes = normalizeCodes(selectedSkillCodes);
        if (normalizedSkillCodes.isEmpty() || StrUtil.isBlank(plainContent)) {
            return plainContent;
        }
        // 步骤 1：仅移除已选技能 token，避免误删用户真正想表达的其他 @ 内容。
        StringBuilder strippedContent = new StringBuilder();
        Matcher matcher = STANDALONE_MENTION_TOKEN_PATTERN.matcher(plainContent);
        int cursor = 0;
        while (matcher.find()) {
            String mentionedCode = StrUtil.trimToEmpty(matcher.group(1));
            if (!normalizedSkillCodes.contains(mentionedCode)) {
                continue;
            }
            strippedContent.append(plainContent, cursor, matcher.start());
            cursor = matcher.end();
        }
        strippedContent.append(plainContent.substring(cursor));
        // 步骤 2：能力 token 被移除后压缩空白，保证短句判断和落库展示稳定。
        return strippedContent.toString().replaceAll("\\s+", " ").trim();
    }

    /**
     * 构造只供模型消费的消息副本；数据库中的原消息仍保留 @skill 前缀。
     * @param message 原始消息。
     * @return 模型输入消息。
     */
    public static ChatMessage toPlainAiMessage(ChatMessage message) {
        if (message == null || message.getRole() != ChatMessageRole.USER) {
            return message;
        }
        String plainContent = stripSelectedSkillMentions(message.getContent(), parseSkillCodes(message.getContent()));
        if (StrUtil.isBlank(plainContent) || StrUtil.equals(plainContent, message.getContent())) {
            return message;
        }
        ChatMessage plainMessage = ChatMessage.create(
            message.getId(),
            message.getConversationId(),
            message.getRole(),
            plainContent,
            message.getStatus(),
            message.getProvider(),
            message.getModel(),
            message.getErrorMessage()
        ).attachRun(message.getRunId());
        plainMessage.restoreRuntimeState(
            message.getRunId(),
            message.getThinkingContent(),
            message.getThinkingDuration(),
            message.getCreatedAt(),
            message.getUpdatedAt()
        );
        return plainMessage;
    }

    /**
     * 合并显式参数与正文前缀里的技能编码，适配历史重放和手写 @skill 两类入口。
     * @param selectedSkillCodes 请求显式技能编码。
     * @param content 用户正文。
     * @return 去重后的技能编码。
     */
    public static List<String> mergeSkillCodes(List<String> selectedSkillCodes, String content) {
        LinkedHashSet<String> mergedSkillCodes = new LinkedHashSet<>(normalizeCodes(selectedSkillCodes));
        mergedSkillCodes.addAll(parseSkillCodes(content));
        return List.copyOf(mergedSkillCodes);
    }

    private static List<String> normalizeCodes(List<String> codes) {
        if (CollUtil.isEmpty(codes)) {
            return List.of();
        }
        LinkedHashSet<String> normalizedCodes = new LinkedHashSet<>();
        for (String code : codes) {
            if (StrUtil.isNotBlank(code)) {
                normalizedCodes.add(code.trim());
            }
        }
        return List.copyOf(normalizedCodes);
    }
}
