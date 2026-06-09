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

    /** Prompt 模板加载器，用于读取问题改写阶段的系统提示词。 */
    private final PromptTemplateLoader promptTemplateLoader;
    /** AI Prompt 执行服务，用于生成结构化改写结果和子问题拆分。 */
    private final AiPromptExecutionService aiPromptExecutionService;
    /** 查询词映射服务，用于模型改写前先做业务术语归一化。 */
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
        // 步骤 1：空问题直接返回原值，避免把无效输入送入模型改写。
        if (StrUtil.isBlank(question)) {
            return new ConversationRewriteResult(question, false, List.of());
        }
        // 步骤 2：先做查询词映射归一化；明显无需改写的问题走快速旁路，降低主链路延迟。
        String normalizedQuestion = conversationQueryTermMappingService.normalize(question);
        if (shouldBypassPromptRewrite(history, normalizedQuestion)) {
            logRewriteResult(question, normalizedQuestion);
            return new ConversationRewriteResult(normalizedQuestion, false, List.of(normalizedQuestion));
        }
        // 步骤 3：组装 Prompt 并解析模型 JSON 结果，解析失败时回退归一化问题。
        String prompt = promptTemplateLoader.load("rewrite");
        String userPrompt = buildRewriteInput(history, normalizedQuestion);
        try {
            String raw = aiPromptExecutionService.complete(prompt, userPrompt);
            JSONObject root = parseRewritePayload(raw);
            if (root == null) {
                logRewriteFallback(question, normalizedQuestion, "模型未返回 JSON 改写结果");
                return new ConversationRewriteResult(normalizedQuestion, false, List.of(normalizedQuestion));
            }
            // 步骤 4：规范化 rewrite、should_split 和 sub_questions，保证下游总能拿到至少一个问题。
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
            // 步骤 5：模型异常或 JSON 字段异常统一降级，保留堆栈供后端排查。
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
        boolean hasPriorUserContext = hasPriorUserContext(history, question);
        if (hasPriorUserContext && mayDependOnPriorContext(question)) {
            return false;
        }
        if (question.contains("销售")) {
            return true;
        }
        return isSelfContainedSingleQuestion(question);
    }

    /**
     * 判断历史中是否存在上一轮用户上下文。
     * 主聊天链路会先把本轮用户消息加入 history 再调用改写，因此只看 history 是否为空会让首轮旁路失效；
     * 这里把与当前问题相同的本轮输入视为无历史，只要出现其他用户问题就保留模型改写用于指代补全。
     * @param history 用户历史问题列表，可能已经包含本轮问题。
     * @param question 当前问题。
     * @return true 表示存在需要模型补全的上一轮上下文。
     */
    private boolean hasPriorUserContext(List<String> history, String question) {
        if (history == null || history.isEmpty()) {
            return false;
        }
        String normalizedQuestion = normalizeForHistoryCompare(question);
        for (String item : history) {
            String normalizedHistoryItem = normalizeForHistoryCompare(item);
            if (StrUtil.isNotBlank(normalizedHistoryItem) && !normalizedHistoryItem.equals(normalizedQuestion)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 归一化历史问题文本，避免标点和空白导致本轮输入被误判为上一轮上下文。
     */
    private String normalizeForHistoryCompare(String value) {
        return StrUtil.blankToDefault(value, "")
            .replaceAll("[\\p{Punct}\\s，。？！、：；“”‘’（）【】《》]+", "")
            .toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 判断本轮问题是否明显依赖上一轮上下文。
     * 业务意图：长会话里大量问题是新的独立问题，不能因为 history 非空就每轮等待改写模型；
     * 只有包含指代、延续、上文引用或省略动作的问题，才保留改写模型用于补全上下文。
     * @param question 当前问题。
     * @return true 表示需要结合上一轮上下文改写。
     */
    private boolean mayDependOnPriorContext(String question) {
        String normalizedQuestion = normalizeForHistoryCompare(question);
        if (StrUtil.isBlank(normalizedQuestion)) {
            return false;
        }
        return StrUtil.containsAny(
            normalizedQuestion,
            "这个",
            "那个",
            "上面",
            "前面",
            "刚才",
            "上一轮",
            "上一个",
            "继续",
            "接着",
            "再来",
            "按照刚才",
            "基于上面",
            "帮我改",
            "怎么改",
            "这个怎么",
            "那怎么"
        );
    }

    /**
     * 判断首轮问题是否已经足够自包含，可直接进入意图识别和模型回答。
     * 业务意图：改写模型主要用于补全历史指代和拆分多诉求；首轮单一问题没有这些需求时，
     * 继续调用改写模型只会增加回答前等待。搜索/时效类问题保留改写入口，避免影响搜索证据链。
     * @param question 已完成术语归一化的问题。
     * @return true 表示可跳过改写 Prompt。
     */
    private boolean isSelfContainedSingleQuestion(String question) {
        String trimmedQuestion = StrUtil.trim(question);
        if (StrUtil.isBlank(trimmedQuestion) || trimmedQuestion.length() > 80) {
            return false;
        }
        if (isGreetingQuestion(trimmedQuestion)
            || mayNeedQuestionSplit(trimmedQuestion)
            || mayNeedFreshSearchRewrite(trimmedQuestion)) {
            return false;
        }
        return trimmedQuestion.length() >= 3;
    }

    /**
     * 简单问候已由聊天主流程快答处理；改写服务保留原模型兼容测试和兜底语义。
     */
    private boolean isGreetingQuestion(String question) {
        String normalized = question.replaceAll("[\\p{Punct}\\s，。？！、：；“”‘’（）【】《》]+", "")
            .toLowerCase(java.util.Locale.ROOT);
        return List.of("你好", "您好", "你好呀", "你好啊", "嗨", "哈喽", "hello", "hi", "在吗").contains(normalized);
    }

    /**
     * 多诉求问题需要保留模型拆分能力，避免把搜索、MCP 和普通回答混在一个子问题里路由。
     */
    private boolean mayNeedQuestionSplit(String question) {
        return StrUtil.containsAny(question, "分别", "然后", "以及", "同时", "顺便", "并且", "另外", "接着", "\n", "；", ";");
    }

    /**
     * 时效和显式搜索问题保留改写模型入口，避免影响后续搜索问题抽取和权威证据排序。
     */
    private boolean mayNeedFreshSearchRewrite(String question) {
        return StrUtil.containsAny(question, "搜索", "搜一下", "联网", "查询", "查一下", "最新", "最近", "今天", "当前", "现在", "版本", "汇率", "新闻", "发布");
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
        // 步骤 1：仅处理看起来以 fenced code block 开头的文本，普通 JSON 或普通文本原样返回。
        String value = StrUtil.trim(raw);
        if (StrUtil.isBlank(value) || !value.startsWith("```")) {
            return value;
        }
        String[] lines = value.split("\\R", -1);
        if (lines.length < 2 || !StrUtil.trim(lines[0]).startsWith("```")) {
            return value;
        }
        // 步骤 2：从尾部查找闭合 fence；找不到闭合标记时不做截断，避免误删模型输出。
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
        // 步骤 3：只拼接 fence 内部内容，供 Hutool JSON 解析器读取。
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
