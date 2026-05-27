package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责根据会话上下文改写用户问题，为搜索与意图识别提供更稳定的输入。
 */
@Service
@RequiredArgsConstructor
public class ConversationRewriteService {

    private final PromptTemplateLoader promptTemplateLoader;
    private final AiPromptExecutionService aiPromptExecutionService;
    private final ConversationQueryTermMappingService conversationQueryTermMappingService;

    /**
     * 使用 Prompt 驱动方式改写当前问题，失败时回退原问题。
     * @param history 历史上下文摘要。
     * @param question 当前问题。
     * @return 改写后的问题。
     */
    @ConversationTraceNode(name = "rewrite-question", type = "REWRITE")
    public String rewrite(List<String> history, String question) {
        return rewriteResult(history, question).rewrite();
    }

    /**
     * 返回完整的改写结果，包含主问题、拆分标记和子问题集合。
     * @param history 历史上下文摘要。
     * @param question 当前问题。
     * @return 改写结果。
     */
    @ConversationTraceNode(name = "rewrite-with-split", type = "REWRITE")
    public ConversationRewriteResult rewriteResult(List<String> history, String question) {
        if (StrUtil.isBlank(question)) {
            return new ConversationRewriteResult(question, false, List.of());
        }
        String normalizedQuestion = enhanceAuthoritativeLatestQuestion(conversationQueryTermMappingService.normalize(question));
        if (shouldBypassPromptRewrite(history, normalizedQuestion)) {
            return new ConversationRewriteResult(normalizedQuestion, false, List.of(normalizedQuestion));
        }
        String prompt = promptTemplateLoader.load("rewrite");
        String userPrompt = buildRewriteInput(history, normalizedQuestion);
        try {
            String raw = aiPromptExecutionService.complete(prompt, userPrompt);
            JSONObject root = JSONUtil.parseObj(raw);
            String rewrite = StrUtil.trim(root.getStr("rewrite"));
            boolean shouldSplit = Boolean.TRUE.equals(root.getBool("should_split"));
            List<String> subQuestions = root.getJSONArray("sub_questions") == null
                ? List.of()
                : root.getJSONArray("sub_questions").stream()
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(StrUtil::isNotBlank)
                    .toList();
            String resolvedRewrite = StrUtil.isBlank(rewrite) ? normalizedQuestion : rewrite;
            List<String> resolvedSubQuestions = subQuestions.isEmpty() ? List.of(resolvedRewrite) : subQuestions;
            return new ConversationRewriteResult(resolvedRewrite, shouldSplit && resolvedSubQuestions.size() > 1, resolvedSubQuestions);
        } catch (Exception exception) {
            return new ConversationRewriteResult(normalizedQuestion, false, List.of(normalizedQuestion));
        }
    }

    /**
     * 组装改写阶段的用户输入，明确区分历史与当前问题。
     * @param history 历史消息。
     * @param question 当前问题。
     * @return 改写输入文本。
     */
    private String buildRewriteInput(List<String> history, String question) {
        String latestHistory = history == null || history.isEmpty() ? "无" : history.getLast();
        return "历史上下文：" + latestHistory + "\n当前问题：" + question;
    }

    /**
     * 对 GPT/OpenAI 最新模型问题补充官方文档搜索锚点，降低旧摘要和第三方传言进入证据首位的概率。
     */
    private String enhanceAuthoritativeLatestQuestion(String question) {
        if (StrUtil.isBlank(question)) {
            return question;
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        boolean latestIntent = normalized.contains("最新")
            || normalized.contains("当前")
            || normalized.contains("现在")
            || normalized.contains("latest")
            || normalized.contains("current");
        boolean openAiModelIntent = normalized.contains("gpt") || normalized.contains("openai");
        if (!latestIntent || !openAiModelIntent || normalized.contains("developers.openai.com")) {
            return question;
        }
        return question + " OpenAI 官方文档 latest model developers.openai.com";
    }
    /**
     * 对明显无需 LLM 改写的短问题做快速旁路，避免主链路在简单统计问句上额外等待。
     * @param history 历史上下文。
     * @param question 规范化后的问题。
     * @return 是否跳过 Prompt 改写。
     */
    private boolean shouldBypassPromptRewrite(List<String> history, String question) {
        if (history != null && !history.isEmpty()) {
            return false;
        }
        return question.contains("销售");
    }
}