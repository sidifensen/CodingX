package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 面向“最新/当前”类公开信息问题做权威来源与版本号优先排序。
 */
@Component
@Order(200)
public class AuthoritativeLatestSearchPostProcessor implements SearchResultPostProcessor {

    private static final Pattern GPT_VERSION_PATTERN = Pattern.compile("(?i)gpt[-\\s]?(\\d+(?:\\.\\d+)*)");

    @Override
    public List<SearchReferenceCandidate> process(SearchRequestContext context, List<SearchReferenceCandidate> candidates) {
        if (!isLatestModelQuestion(context.question()) || candidates == null || candidates.size() < 2) {
            return candidates;
        }
        return candidates.stream()
            .sorted(Comparator
                .comparingDouble((SearchReferenceCandidate candidate) -> authoritativeLatestScore(candidate)).reversed()
                .thenComparing(Comparator.comparingDouble(
                    (SearchReferenceCandidate candidate) -> candidate.score() == null ? 0D : candidate.score()
                ).reversed()))
            .toList();
    }

    /**
     * 只对同时包含“最新/当前”和模型实体的搜索启用，避免影响普通百科类搜索排序。
     */
    private boolean isLatestModelQuestion(String question) {
        if (StrUtil.isBlank(question)) {
            return false;
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        boolean latestIntent = normalized.contains("最新")
            || normalized.contains("当前")
            || normalized.contains("现在")
            || normalized.contains("latest")
            || normalized.contains("current");
        boolean modelIntent = normalized.contains("gpt")
            || normalized.contains("openai")
            || normalized.contains("模型")
            || normalized.contains("model");
        return latestIntent && modelIntent;
    }

    /**
     * 综合来源权威度和版本号新旧给候选打分，确保官方新版本不被旧页面或第三方传言压住。
     */
    private double authoritativeLatestScore(SearchReferenceCandidate candidate) {
        return officialSourceWeight(candidate) + latestVersionWeight(candidate);
    }

    /**
     * OpenAI 官方文档域名权重最高，官方主站次之，第三方来源保持最低基础权重。
     */
    private double officialSourceWeight(SearchReferenceCandidate candidate) {
        String host = normalizedHost(candidate);
        if ("developers.openai.com".equals(host) || "platform.openai.com".equals(host)) {
            return 10_000D;
        }
        if ("openai.com".equals(host) || host.endsWith(".openai.com")) {
            return 8_000D;
        }
        return 0D;
    }

    /**
     * 从标题、链接和摘要中提取 GPT 版本号，按数值位比较而不是字符串比较。
     */
    private double latestVersionWeight(SearchReferenceCandidate candidate) {
        String evidence = StrUtil.blankToDefault(candidate.title(), "")
            + " "
            + StrUtil.blankToDefault(candidate.url(), "")
            + " "
            + StrUtil.blankToDefault(candidate.snippet(), "");
        Matcher matcher = GPT_VERSION_PATTERN.matcher(evidence);
        double maxWeight = 0D;
        while (matcher.find()) {
            maxWeight = Math.max(maxWeight, versionWeight(matcher.group(1)));
        }
        return maxWeight;
    }

    /**
     * 把形如 5.5.1 的版本号折算成可比较分数，前置数字权重更高。
     */
    private double versionWeight(String version) {
        String[] parts = version.split("\\.");
        double weight = 0D;
        double multiplier = 100D;
        for (String part : parts) {
            try {
                weight += Integer.parseInt(part) * multiplier;
            } catch (NumberFormatException ignored) {
                // 非标准版本片段直接忽略，避免单个异常片段破坏整条搜索结果排序。
            }
            multiplier = multiplier / 100D;
        }
        return weight;
    }

    /**
     * 优先使用 provider 已解析站点名，缺失时再从 URL 解析 host。
     */
    private String normalizedHost(SearchReferenceCandidate candidate) {
        String host = StrUtil.blankToDefault(candidate.siteName(), "");
        if (StrUtil.isBlank(host) && StrUtil.isNotBlank(candidate.url())) {
            try {
                host = StrUtil.blankToDefault(URI.create(candidate.url()).getHost(), "");
            } catch (RuntimeException ignored) {
                host = "";
            }
        }
        host = host.trim().toLowerCase(Locale.ROOT);
        return host.startsWith("www.") ? host.substring(4) : host;
    }
}
