package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import java.util.List;

/**
 * 聊天前置链路轻量判断信号集合。
 * 业务意图：把改写旁路、意图旁路和上下文依赖判断使用的关键词集中维护，避免服务类里散落硬编码文本。
 */
public final class ConversationPreflightSignals {

    /** 中英文标点和空白归一化规则，用于比较问题、示例和短信号词。 */
    public static final String NORMALIZE_PATTERN = "[\\p{Punct}\\s，。？！、：；“”‘’（）【】《》]+";
    /** 普通自包含问题允许走轻量旁路的最大字符数，过长问题保留模型改写以降低误判风险。 */
    public static final int SELF_CONTAINED_QUESTION_MAX_LENGTH = 80;

    /** 简单问候已由聊天主流程快答处理，改写服务识别后不再走普通旁路。 */
    public static final List<String> GREETING_TEXTS = List.of(
        "你好",
        "您好",
        "你好呀",
        "你好啊",
        "嗨",
        "哈喽",
        "hello",
        "hi",
        "在吗",
        "早上好",
        "上午好",
        "中午好",
        "下午好",
        "晚上好"
    );

    /** 上下文依赖信号，命中时保留改写模型补全上一轮指代对象。 */
    public static final List<String> PRIOR_CONTEXT_REFERENCE_MARKERS = List.of(
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

    /** 多诉求信号，命中时保留改写模型拆分子问题。 */
    public static final List<String> MULTI_REQUEST_MARKERS = List.of(
        "分别",
        "然后",
        "以及",
        "同时",
        "顺便",
        "并且",
        "另外",
        "接着",
        "\n",
        "；",
        ";"
    );

    /** 显式搜索或时效信号，命中时保留搜索抽取、意图分类和工具路由能力。 */
    public static final List<String> FRESH_OR_SEARCH_MARKERS = List.of(
        "搜索",
        "搜一下",
        "联网",
        "查询",
        "查一下",
        "最新",
        "最近",
        "今天",
        "当前",
        "现在",
        "版本",
        "汇率",
        "新闻",
        "发布"
    );

    /** 天气 MCP 相关信号，普通直答旁路需要排除这些工具类问题。 */
    public static final List<String> WEATHER_TOOL_MARKERS = List.of(
        "天气",
        "温度",
        "下雨",
        "空气质量"
    );

    /** 普通直答信号，命中且没有高置信配置候选时可直接回落 chat.normal。 */
    public static final List<String> PLAIN_DIRECT_MARKERS = List.of(
        "解释",
        "介绍",
        "分析",
        "总结",
        "梳理",
        "说明",
        "帮我写",
        "写一个",
        "生成",
        "翻译",
        "优化",
        "改写",
        "润色",
        "代码",
        "怎么",
        "如何"
    );

    /** 可配置统计问句旁路信号，保留既有销售统计类短问句快速路径。 */
    public static final List<String> METRIC_SHORT_QUESTION_MARKERS = List.of("销售");

    private ConversationPreflightSignals() {
    }

    /**
     * 统一标准化问句、示例和信号词，减少标点、空白和大小写差异带来的误判。
     * @param value 原始文本。
     * @return 标准化文本。
     */
    public static String normalizeText(String value) {
        return value == null ? "" : value.replaceAll(NORMALIZE_PATTERN, "").toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 判断原始文本是否包含任一原始信号词，适用于换行和中文分号这类不应先归一化的场景。
     * @param value 原始文本。
     * @param markers 信号词集合。
     * @return true 表示命中。
     */
    public static boolean containsAnyRaw(String value, List<String> markers) {
        if (StrUtil.isBlank(value) || markers == null || markers.isEmpty()) {
            return false;
        }
        return StrUtil.containsAny(value, markers.toArray(String[]::new));
    }

    /**
     * 判断已归一化文本是否包含任一信号词，信号词会使用同一规则归一化后比较。
     * @param normalizedValue 已归一化文本。
     * @param markers 信号词集合。
     * @return true 表示命中。
     */
    public static boolean containsAnyNormalized(String normalizedValue, List<String> markers) {
        if (StrUtil.isBlank(normalizedValue) || markers == null || markers.isEmpty()) {
            return false;
        }
        for (String marker : markers) {
            if (normalizedValue.contains(normalizeText(marker))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断问题是否为简单问候。
     * @param question 当前问题。
     * @return true 表示简单问候。
     */
    public static boolean isGreetingQuestion(String question) {
        return GREETING_TEXTS.contains(normalizeText(question));
    }

    /**
     * 判断问题是否可能依赖上一轮上下文。
     * @param question 当前问题。
     * @return true 表示需要保留改写模型补全上下文。
     */
    public static boolean mayDependOnPriorContext(String question) {
        return containsAnyNormalized(normalizeText(question), PRIOR_CONTEXT_REFERENCE_MARKERS);
    }

    /**
     * 判断问题是否包含多诉求拆分信号。
     * @param question 当前问题。
     * @return true 表示应保留改写拆分能力。
     */
    public static boolean mayNeedQuestionSplit(String question) {
        return containsAnyRaw(question, MULTI_REQUEST_MARKERS);
    }

    /**
     * 判断问题是否包含搜索、时效或工具路由信号。
     * @param question 当前问题。
     * @return true 表示应保留改写或意图分类链路。
     */
    public static boolean hasFreshSearchOrToolSignal(String question) {
        String normalizedQuestion = normalizeText(question);
        return containsAnyNormalized(normalizedQuestion, FRESH_OR_SEARCH_MARKERS)
            || containsAnyNormalized(normalizedQuestion, WEATHER_TOOL_MARKERS);
    }

    /**
     * 判断问题是否包含搜索或时效信号。
     * @param question 当前问题。
     * @return true 表示改写阶段应保留搜索问题抽取能力。
     */
    public static boolean hasFreshSearchSignal(String question) {
        return containsAnyNormalized(normalizeText(question), FRESH_OR_SEARCH_MARKERS);
    }

    /**
     * 判断问题是否属于普通模型可直接回答的写作、解释、分析或代码类请求。
     * @param question 当前问题。
     * @return true 表示可考虑普通直答旁路。
     */
    public static boolean isPlainDirectQuestion(String question) {
        return containsAnyNormalized(normalizeText(question), PLAIN_DIRECT_MARKERS);
    }
}
