package com.codingx.automation.application.service;

import cn.hutool.core.convert.NumberChineseFormatter;
import cn.hutool.core.util.StrUtil;
import com.codingx.automation.domain.model.AutomationScheduleType;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 自动化创建意图解析器，使用保守确定性规则从聊天文本中提取计划和需求。
 */
@Component
public class AutomationTaskIntentParser {

    /** 匹配 18:11、18点11、18 点等中文常见时间写法。 */
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d{1,2})(?:[:：点时])(\\d{1,2})?");
    /** 匹配十二点、两点半这类中文整点/半点写法，只在紧跟点/时时视为时间。 */
    private static final Pattern CHINESE_HOUR_PATTERN = Pattern.compile("([零〇一二两三四五六七八九十]{1,3})(?:点|时)(半)?");
    /** 匹配每周几表达，1-7 与一到日都支持。 */
    private static final Pattern WEEKLY_PATTERN = Pattern.compile("每周([一二三四五六日天1-7])");

    /**
     * 解析聊天文本中的自动化创建意图；不明确时返回空，交回普通聊天链路。
     * @param content 用户消息正文。
     * @param now 当前时间。
     * @return 解析结果。
     */
    public Optional<ParsedAutomationIntent> parse(String content, LocalDateTime now) {
        String normalizedContent = StrUtil.trimToEmpty(content);
        if (!hasAutomationSignal(normalizedContent)) {
            return Optional.empty();
        }
        LocalTime scheduleTime = parseTime(normalizedContent).orElse(null);
        AutomationScheduleType scheduleType = resolveScheduleType(normalizedContent);
        if (scheduleType != AutomationScheduleType.ONCE && scheduleTime == null) {
            return Optional.empty();
        }
        Integer dayOfWeek = resolveDayOfWeek(normalizedContent);
        LocalDateTime onceExecuteAt = null;
        if (scheduleType == AutomationScheduleType.ONCE) {
            if (scheduleTime == null) {
                return Optional.empty();
            }
            LocalDateTime candidate = LocalDateTime.of(now.toLocalDate(), scheduleTime);
            onceExecuteAt = candidate.isAfter(now) ? candidate : candidate.plusDays(1);
        }
        String prompt = normalizePrompt(normalizedContent);
        if (StrUtil.isBlank(prompt)) {
            return Optional.empty();
        }
        String name = buildTaskName(prompt);
        return Optional.of(new ParsedAutomationIntent(
            name,
            prompt,
            scheduleType,
            scheduleTime == null ? null : String.format("%02d:%02d", scheduleTime.getHour(), scheduleTime.getMinute()),
            dayOfWeek,
            onceExecuteAt
        ));
    }

    /**
     * 判断文本是否具备足够明确的创建自动化信号。
     */
    private boolean hasAutomationSignal(String content) {
        if (StrUtil.isBlank(content)) {
            return false;
        }
        boolean hasSchedule = StrUtil.containsAny(content, "每天", "每日", "每周", "明天", "今晚", "定时", "自动化");
        boolean hasCreateVerb = StrUtil.containsAny(content, "创建", "设置", "设定", "安排", "帮我", "提醒", "自动");
        return hasSchedule && hasCreateVerb;
    }

    /**
     * 解析计划类型，默认把带每天/每日/定时的请求视为每日任务。
     */
    private AutomationScheduleType resolveScheduleType(String content) {
        if (StrUtil.contains(content, "每周")) {
            return AutomationScheduleType.WEEKLY;
        }
        if (StrUtil.containsAny(content, "明天", "今晚", "一次", "一次性")) {
            return AutomationScheduleType.ONCE;
        }
        return AutomationScheduleType.DAILY;
    }

    /**
     * 从文本中提取 HH:mm 形式的执行时间。
     */
    private Optional<LocalTime> parseTime(String content) {
        Matcher matcher = TIME_PATTERN.matcher(content);
        if (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            int minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return Optional.empty();
            }
            return Optional.of(LocalTime.of(hour, minute));
        }
        return parseChineseHour(content);
    }

    /**
     * 解析中文数字整点/半点。只接受 0-23 小时，避免把普通数量词误判为时间。
     */
    private Optional<LocalTime> parseChineseHour(String content) {
        Matcher matcher = CHINESE_HOUR_PATTERN.matcher(content);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int hour = NumberChineseFormatter.chineseToNumber(matcher.group(1));
        int minute = matcher.group(2) == null ? 0 : 30;
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return Optional.empty();
        }
        return Optional.of(LocalTime.of(hour, minute));
    }

    /**
     * 解析每周任务执行星期。
     */
    private Integer resolveDayOfWeek(String content) {
        Matcher matcher = WEEKLY_PATTERN.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        return switch (matcher.group(1)) {
            case "一", "1" -> 1;
            case "二", "2" -> 2;
            case "三", "3" -> 3;
            case "四", "4" -> 4;
            case "五", "5" -> 5;
            case "六", "6" -> 6;
            default -> 7;
        };
    }

    /**
     * 去掉计划和创建动词，保留真正要执行的需求说明。
     */
    private String normalizePrompt(String content) {
        String prompt = content
            .replaceAll("(?:创建|设置|设定|安排)(?:一个|一条|个|条)?(?:自动化|定时)?任务", "")
            .replaceAll("自动化任务|定时任务", "")
            .replaceAll("每天|每日|每周[一二三四五六日天1-7]?|明天|今晚|一次性?|定时|自动化", "")
            .replaceAll("\\d{1,2}[:：点时]\\d{0,2}", "")
            .replaceAll("[零〇一二两三四五六七八九十]{1,3}[点时]半?", "")
            .replaceAll("帮我|请|创建|设置|设定|安排|提醒我|提醒|自动", "")
            .replaceAll("[，。,.\\s]+", " ")
            .trim();
        return StrUtil.blankToDefault(prompt, content.trim());
    }

    /**
     * 由需求摘要生成短任务名，避免聊天创建任务名称过长。
     */
    private String buildTaskName(String prompt) {
        String compactPrompt = prompt.replaceAll("\\s+", "");
        return StrUtil.maxLength(compactPrompt, 20);
    }

    /**
     * 聊天文本解析出的自动化计划草稿。
     *
     * @param name 任务名称。
     * @param prompt 任务需求说明。
     * @param scheduleType 计划类型。
     * @param scheduleTime 固定执行时间。
     * @param scheduleDayOfWeek 每周执行星期。
     * @param onceExecuteAt 一次性执行时间。
     */
    public record ParsedAutomationIntent(
        String name,
        String prompt,
        AutomationScheduleType scheduleType,
        String scheduleTime,
        Integer scheduleDayOfWeek,
        LocalDateTime onceExecuteAt
    ) {
    }
}
