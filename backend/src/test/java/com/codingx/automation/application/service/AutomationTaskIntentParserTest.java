package com.codingx.automation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.automation.domain.model.AutomationScheduleType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 验证聊天自动化创建意图解析器的保守命中规则。
 */
class AutomationTaskIntentParserTest {

    /**
     * 明确包含“每天 + 时间 + 帮我”的请求应解析为每日自动化任务。
     */
    @Test
    void parseShouldResolveDailyAutomationIntent() {
        AutomationTaskIntentParser parser = new AutomationTaskIntentParser();

        var result = parser.parse("每天 18:11 帮我总结项目状态", LocalDateTime.of(2026, 6, 8, 17, 0));

        assertTrue(result.isPresent());
        assertEquals(AutomationScheduleType.DAILY, result.get().scheduleType());
        assertEquals("18:11", result.get().scheduleTime());
        assertEquals("总结项目状态", result.get().prompt());
    }

    /**
     * 没有明确计划时间的普通问题不应误创建自动化任务。
     */
    @Test
    void parseShouldIgnoreNormalChatWithoutSchedule() {
        AutomationTaskIntentParser parser = new AutomationTaskIntentParser();

        var result = parser.parse("帮我制定每天学习计划", LocalDateTime.of(2026, 6, 8, 17, 0));

        assertTrue(result.isEmpty());
    }
}
