package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 面向“最新/当前”类公开信息问题做权威来源与时效信号优先排序。
 */
@Component
@Order(200)
public class AuthoritativeLatestSearchPostProcessor implements SearchResultPostProcessor {

    private static final Pattern VERSION_PATTERN = Pattern.compile("(?<!\\d)(\\d+(?:\\.\\d+){1,3})(?!\\d)");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(?<!\\d)(20\\d{2})[-/.](0?[1-9]|1[0-2])[-/.](0?[1-9]|[12]\\d|3[01])(?!\\d)");
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("(?<!\\d)(20\\d{2})[-/.](0?[1-9]|1[0-2])(?!\\d)");
    private static final Pattern YEAR_PATTERN = Pattern.compile("(?<!\\d)(20\\d{2})(?!\\d)");
    private static final int MAX_VERSION_SEGMENTS = 4;

    @Override
    public List<SearchReferenceCandidate> process(SearchRequestContext context, List<SearchReferenceCandidate> candidates) {
        if (!isFreshnessQuestion(context.question()) || candidates == null || candidates.size() < 2) {
            return candidates;
        }
        return candidates.stream()
            .sorted(Comparator
                .comparing((SearchReferenceCandidate candidate) -> rankingSignals(candidate), Comparator.reverseOrder())
                .thenComparing(Comparator.comparingDouble(
                    (SearchReferenceCandidate candidate) -> candidate.score() == null ? 0D : candidate.score()
                ).reversed()))
            .toList();
    }

    /**
     * 只对包含新鲜度意图的问题启用，避免影响普通百科类搜索排序。
     */
    private boolean isFreshnessQuestion(String question) {
        if (StrUtil.isBlank(question)) {
            return false;
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        return normalized.contains("最新")
            || normalized.contains("当前")
            || normalized.contains("现在")
            || normalized.contains("latest")
            || normalized.contains("current")
            || normalized.contains("version")
            || normalized.contains("版本")
            || normalized.contains("release")
            || normalized.contains("发布");
    }

    /**
     * 综合来源权威度、版本号、日期和未确认语义给候选打分，避免单纯搜索分数压过更新权威证据。
     */
    private RankingSignals rankingSignals(SearchReferenceCandidate candidate) {
        return new RankingSignals(
            sourceAuthority(candidate),
            newestVersion(candidate),
            newestDate(candidate),
            uncertaintyPenalty(candidate)
        );
    }

    /**
     * 根据通用站点形态判断权威度，不绑定任何具体厂商或产品名。
     */
    private int sourceAuthority(SearchReferenceCandidate candidate) {
        String host = normalizedHost(candidate);
        String url = StrUtil.blankToDefault(candidate.url(), "").toLowerCase(Locale.ROOT);
        String title = StrUtil.blankToDefault(candidate.title(), "").toLowerCase(Locale.ROOT);
        if (host.startsWith("docs.") || host.contains(".docs.") || host.startsWith("developer.") || host.startsWith("developers.")) {
            return 40;
        }
        if (host.startsWith("help.") || host.startsWith("support.") || host.startsWith("learn.")) {
            return 32;
        }
        if (containsAny(url, "/docs", "/documentation", "/release", "/releases", "/changelog", "/versions", "/download")
            || containsAny(title, "docs", "documentation", "release notes", "changelog", "版本说明", "发布说明")) {
            return 24;
        }
        if (containsAny(url, "blog", "news", "forum", "community", "medium.com", "reddit.com")
            || containsAny(title, "rumor", "leak", "传言", "爆料", "泄露")) {
            return 4;
        }
        return 12;
    }

    /**
     * 从标题、链接和摘要中提取通用语义版本号，按数值位比较而不是字符串比较。
     */
    private Version newestVersion(SearchReferenceCandidate candidate) {
        String evidence = evidenceText(candidate);
        Matcher matcher = VERSION_PATTERN.matcher(evidence);
        Version newest = Version.empty();
        while (matcher.find()) {
            Version version = Version.parse(matcher.group(1));
            if (version.compareTo(newest) > 0) {
                newest = version;
            }
        }
        return newest;
    }

    /**
     * 从候选内容中提取可比较日期，作为没有清晰版本号时的次级新鲜度信号。
     */
    private LocalDate newestDate(SearchReferenceCandidate candidate) {
        String evidence = evidenceText(candidate);
        LocalDate newest = LocalDate.MIN;
        Matcher dateMatcher = ISO_DATE_PATTERN.matcher(evidence);
        while (dateMatcher.find()) {
            LocalDate parsed = parseDate(dateMatcher.group(1), dateMatcher.group(2), dateMatcher.group(3));
            if (parsed.isAfter(newest)) {
                newest = parsed;
            }
        }
        Matcher monthMatcher = YEAR_MONTH_PATTERN.matcher(evidence);
        while (monthMatcher.find()) {
            LocalDate parsed = parseDate(monthMatcher.group(1), monthMatcher.group(2), "1");
            if (parsed.isAfter(newest)) {
                newest = parsed;
            }
        }
        Matcher yearMatcher = YEAR_PATTERN.matcher(evidence);
        while (yearMatcher.find()) {
            LocalDate parsed = parseDate(yearMatcher.group(1), "1", "1");
            if (parsed.isAfter(newest)) {
                newest = parsed;
            }
        }
        return newest;
    }

    /**
     * 第三方传言、泄露和未确认表述不应仅凭更高版本号压过权威证据。
     */
    private int uncertaintyPenalty(SearchReferenceCandidate candidate) {
        String evidence = evidenceText(candidate).toLowerCase(Locale.ROOT);
        return containsAny(evidence, "rumor", "leak", "unconfirmed", "reportedly", "传言", "爆料", "泄露", "未经确认") ? -20 : 0;
    }

    /**
     * 合并候选中的可检索文本，统一供版本、日期和语义信号提取使用。
     */
    private String evidenceText(SearchReferenceCandidate candidate) {
        return StrUtil.blankToDefault(candidate.title(), "")
            + " "
            + StrUtil.blankToDefault(candidate.url(), "")
            + " "
            + StrUtil.blankToDefault(candidate.snippet(), "");
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

    /**
     * 判断文本是否包含任一通用权威或不确定性关键词。
     */
    private boolean containsAny(String text, String... needles) {
        if (StrUtil.isBlank(text)) {
            return false;
        }
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 容错解析搜索结果中的日期片段，异常日期不参与排序。
     */
    private LocalDate parseDate(String year, String month, String day) {
        try {
            return LocalDate.parse(
                String.format("%04d-%02d-%02d", Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day)),
                DateTimeFormatter.ISO_LOCAL_DATE
            );
        } catch (DateTimeParseException | NumberFormatException exception) {
            return LocalDate.MIN;
        }
    }

    /**
     * 后处理排序信号：来源权威度优先，其次版本号和日期；未确认内容通过负权重降低排序。
     */
    private record RankingSignals(int authority, Version version, LocalDate date, int certainty) implements Comparable<RankingSignals> {

        @Override
        public int compareTo(RankingSignals other) {
            int authorityComparison = Integer.compare(authority, other.authority);
            if (authorityComparison != 0) {
                return authorityComparison;
            }
            int versionComparison = version.compareTo(other.version);
            if (versionComparison != 0) {
                return versionComparison;
            }
            int dateComparison = date.compareTo(other.date);
            if (dateComparison != 0) {
                return dateComparison;
            }
            return Integer.compare(certainty, other.certainty);
        }
    }

    /**
     * 用固定段数保存语义版本，保证 2.10 能正确排在 2.9 之后。
     */
    private record Version(List<Integer> segments) implements Comparable<Version> {

        private static Version empty() {
            return new Version(List.of());
        }

        private static Version parse(String rawVersion) {
            String[] rawSegments = rawVersion.split("\\.");
            List<Integer> parsedSegments = new ArrayList<>();
            for (int index = 0; index < Math.min(rawSegments.length, MAX_VERSION_SEGMENTS); index++) {
                try {
                    parsedSegments.add(Integer.parseInt(rawSegments[index]));
                } catch (NumberFormatException exception) {
                    parsedSegments.add(0);
                }
            }
            return new Version(List.copyOf(parsedSegments));
        }

        @Override
        public int compareTo(Version other) {
            int maxLength = Math.max(segments.size(), other.segments.size());
            for (int index = 0; index < maxLength; index++) {
                int current = index < segments.size() ? segments.get(index) : 0;
                int compared = index < other.segments.size() ? other.segments.get(index) : 0;
                int comparison = Integer.compare(current, compared);
                if (comparison != 0) {
                    return comparison;
                }
            }
            return Integer.compare(segments.size(), other.segments.size());
        }
    }
}
