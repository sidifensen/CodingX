package com.codingx.mcp.application.executor;

import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 解析天气 MCP 问题中的必要参数，供意图路由保护和工具执行器复用。
 */
@Component
public class WeatherQuestionParser {

    private static final List<String> WEATHER_WORDS = List.of("天气", "气温", "温度", "预报");
    private static final List<String> FORECAST_WORDS = List.of("预报", "未来", "后天", "明天");
    private static final List<String> INVALID_CITY_WORDS = List.of(
        "天气", "气温", "温度", "预报", "今天", "明天", "后天", "未来",
        "那个", "这个", "附近", "当前位置", "这里", "那里", "哪儿", "哪里"
    );

    /**
     * 解析天气查询参数。
     *
     * @param question 用户问题。
     * @return 天气查询上下文。
     */
    public WeatherQueryContext parse(String question) {
        return parse(question, null);
    }

    /**
     * 解析天气查询参数，优先采用外部已抽取的城市名。
     *
     * @param question 用户问题。
     * @param extractedCity 外部抽取到的城市名，可为空。
     * @return 天气查询上下文。
     */
    public WeatherQueryContext parse(String question, String extractedCity) {
        String safeQuestion = StrUtil.blankToDefault(question, "");
        String city = extractCity(safeQuestion, extractedCity);
        String queryType = containsAny(safeQuestion, FORECAST_WORDS) ? "forecast" : "current";
        int days = parseDays(safeQuestion, 3);
        return new WeatherQueryContext(city, queryType, days);
    }

    /**
     * 判断问题是否已包含可用城市槽位。
     *
     * @param question 用户问题。
     * @return 包含城市返回 true。
     */
    public boolean hasCity(String question) {
        return StrUtil.isNotBlank(parse(question).city());
    }

    /**
     * 从问题文本或外部抽取结果中获取城市。
     */
    private String extractCity(String question, String extractedCity) {
        String aiCity = normalizeCityKeyword(extractedCity);
        if (isValidCity(aiCity)) {
            return aiCity;
        }
        String explicitCity = firstValidCity(
            ReUtil.get(
                "(?:帮我查一下|帮我查|帮我看一下|帮我看|查一下|查询|查|看看|看下|了解|请问)?([\\p{IsHan}]{2,8}?)(?:市|区|县|州|盟)?(?:今天|明天|后天|未来\\d+天|未来[一二三四五六七八九十两]+天)?(?:的)?(?:天气|气温|温度|预报)",
                question,
                1
            ),
            ReUtil.get(
                "(?:在|到|去)([\\p{IsHan}]{2,8}?)(?:市|区|县|州|盟)?[，,\\s]*(?:今天|明天|后天|未来\\d+天|未来[一二三四五六七八九十两]+天)?(?:的)?(?:天气|气温|温度|预报)",
                question,
                1
            )
        );
        if (StrUtil.isNotBlank(explicitCity)) {
            return explicitCity;
        }
        String fallbackCity = firstValidCity(ReUtil.get(
            "(北京|上海|广州|深圳|杭州|成都|武汉|南京|西安|重庆|长沙|天津|苏州|郑州|青岛|大连|厦门|昆明|哈尔滨|三亚)",
            question,
            1
        ));
        return StrUtil.isBlank(fallbackCity) ? null : fallbackCity;
    }

    /**
     * 取第一个合法城市候选，避免把“今天”“那个”等上下文词误当城市。
     */
    private String firstValidCity(String... candidates) {
        for (String candidate : candidates) {
            String normalized = normalizeCityKeyword(candidate);
            if (isValidCity(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    /**
     * 过滤明显不是城市的时间词、指代词和天气关键词。
     */
    private boolean isValidCity(String city) {
        if (StrUtil.isBlank(city) || StrUtil.length(city) < 2) {
            return false;
        }
        for (String invalidWord : INVALID_CITY_WORDS) {
            if (StrUtil.equals(city, invalidWord) || StrUtil.contains(city, invalidWord)) {
                return false;
            }
        }
        return WEATHER_WORDS.stream().noneMatch(city::contains);
    }

    /**
     * 规范化城市关键词，移除口语助词等非地理实体尾缀。
     */
    private String normalizeCityKeyword(String city) {
        String normalized = StrUtil.trim(city);
        if (StrUtil.isBlank(normalized)) {
            return normalized;
        }
        // 兼容口语表达中尾部助词，避免污染地理编码查询入参。
        while (StrUtil.endWith(normalized, "的")) {
            normalized = StrUtil.removeSuffix(normalized, "的");
        }
        return normalized;
    }

    /**
     * 从文本中解析预报天数。
     */
    private int parseDays(String question, int defaultDays) {
        String digitDays = ReUtil.get("(\\d+)\\s*天", question, 1);
        if (StrUtil.isNotBlank(digitDays)) {
            return normalizeDays(Integer.parseInt(digitDays));
        }
        String chineseDays = ReUtil.get("([一二三四五六七八九十两]+)天", question, 1);
        if (StrUtil.isNotBlank(chineseDays)) {
            return normalizeDays(parseChineseNumber(chineseDays));
        }
        if (question.contains("明天")) {
            return 2;
        }
        if (question.contains("后天")) {
            return 3;
        }
        return defaultDays;
    }

    /**
     * 中文数字转整数。
     */
    private int parseChineseNumber(String chinese) {
        return switch (chinese) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> 3;
        };
    }

    /**
     * 天数边界保护。
     */
    private int normalizeDays(int days) {
        if (days <= 0) {
            return 3;
        }
        return Math.min(days, 7);
    }

    /**
     * 判断是否命中任一关键词。
     */
    private boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    /**
     * 天气查询上下文。
     *
     * @param city 城市名。
     * @param queryType 查询类型。
     * @param days 预报天数。
     */
    public record WeatherQueryContext(String city, String queryType, int days) {
    }
}
