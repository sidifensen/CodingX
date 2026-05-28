package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 负责根据会话上下文改写用户问题，为搜索与意图识别提供更稳定的输入。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationRewriteService {

    private static final int LOG_QUESTION_PREVIEW_LENGTH = 300;

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
        String normalizedQuestion = conversationQueryTermMappingService.normalize(question);
        if (shouldBypassPromptRewrite(history, normalizedQuestion)) {
            logRewriteResult(question, normalizedQuestion);
            return new ConversationRewriteResult(normalizedQuestion, false, List.of(normalizedQuestion));
        }
        String prompt = promptTemplateLoader.load("rewrite");
        String userPrompt = buildRewriteInput(history, normalizedQuestion);
        try {
            String raw = aiPromptExecutionService.complete(prompt, userPrompt);
            JSONObject root = parseRewritePayload(raw);
            if (root == null) {
                logRewriteFallback(question, normalizedQuestion, "模型未返回 JSON 改写结果");
                return new ConversationRewriteResult(normalizedQuestion, false, List.of(normalizedQuestion));
            }
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
            boolean resolvedShouldSplit = shouldSplit && resolvedSubQuestions.size() > 1;
            logRewriteResult(question, resolvedRewrite);
            return new ConversationRewriteResult(resolvedRewrite, resolvedShouldSplit, resolvedSubQuestions);
        } catch (Exception exception) {
            logRewriteFallback(question, normalizedQuestion, "改写结果解析失败", exception);
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

    /**
     * 按固定格式打印问题改写结果，方便在后端日志中快速对比改写前后文本。
     */
    private void logRewriteResult(String originalQuestion, String rewrittenQuestion) {
        log.info(
            "原问题: {}, 改写后问题: {}",
            logPreview(originalQuestion),
            logPreview(rewrittenQuestion)
        );
    }

    /**
     * 记录改写链路降级原因，便于区分“模型格式不符”和“真正异常”两类情况。
     */
    private void logRewriteFallback(String originalQuestion, String rewrittenQuestion, String reason) {
        log.info(
            "原问题: {}, 改写后问题: {}, 降级原因: {}",
            logPreview(originalQuestion),
            logPreview(rewrittenQuestion),
            reason
        );
    }

    /**
     * 记录改写链路异常降级，保留堆栈用于排查模型输出或解析器行为异常。
     */
    private void logRewriteFallback(String originalQuestion, String rewrittenQuestion, String reason, Exception exception) {
        log.warn(
            "原问题: {}, 改写后问题: {}, 降级原因: {}",
            logPreview(originalQuestion),
            logPreview(rewrittenQuestion),
            reason,
            exception
        );
    }

    /**
     * 兼容模型把 JSON 包在 markdown 代码块中返回，同时规避普通文本触发 Hutool JSON 异常。
     */
    private JSONObject parseRewritePayload(String raw) {
        String cleaned = stripMarkdownCodeFence(raw);
        if (!StrUtil.startWith(cleaned, "{")) {
            return null;
        }
        return JSONUtil.parseObj(cleaned);
    }

    /**
     * 清理 markdown fenced code block，保持和意图解析链路一致的 JSON 读取入口。
     */
    private String stripMarkdownCodeFence(String raw) {
        String value = StrUtil.trim(raw);
        if (StrUtil.isBlank(value) || !value.startsWith("```")) {
            return value;
        }
        String[] lines = value.split("\\R", -1);
        if (lines.length < 2 || !StrUtil.trim(lines[0]).startsWith("```")) {
            return value;
        }
        int endFenceLine = -1;
        for (int index = lines.length - 1; index > 0; index--) {
            if (StrUtil.trim(lines[index]).startsWith("```")) {
                endFenceLine = index;
                break;
            }
        }
        if (endFenceLine <= 0) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 1; index < endFenceLine; index++) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(lines[index]);
        }
        return builder.toString().trim();
    }

    /**
     * 日志只保留问题预览，避免超长输入或拆分结果撑大单行日志。
     */
    private String logPreview(String text) {
        return StrUtil.maxLength(text, LOG_QUESTION_PREVIEW_LENGTH);
    }
}
