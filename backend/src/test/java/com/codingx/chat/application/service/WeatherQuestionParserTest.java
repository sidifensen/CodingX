package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.mcp.application.executor.WeatherQuestionParser;
import org.junit.jupiter.api.Test;

/**
 * 验证天气查询参数解析规则，确保路由层和 MCP 执行器复用同一套城市识别约束。
 */
class WeatherQuestionParserTest {

    private final WeatherQuestionParser parser = new WeatherQuestionParser();

    /**
     * 缺少城市或只有指代词时不能误判为城市，后续应交给路由层澄清。
     */
    @Test
    void hasCityReturnsFalseForMissingOrReferentialCity() {
        assertFalse(parser.hasCity("天气怎么样"));
        assertFalse(parser.hasCity("那个天气怎么样"));
        assertFalse(parser.hasCity("今天天气怎么样"));
    }

    /**
     * 明确城市、上下文式城市表达和非常见城市都应被识别，保证正常天气查询继续进入 MCP。
     */
    @Test
    void hasCityReturnsTrueForExplicitCity() {
        assertTrue(parser.hasCity("上海今天天气怎么样"));
        assertTrue(parser.hasCity("我在北京，天气怎么样"));
        assertTrue(parser.hasCity("合肥天气怎么样"));
    }

    /**
     * AI 已抽取城市时应优先使用 AI 结果，规则只作为降级兜底。
     */
    @Test
    void parsePrefersAiCityWhenProvided() {
        WeatherQuestionParser.WeatherQueryContext context = parser.parse("帮我查一下附近天气", "上海");

        assertEquals("上海", context.city());
        assertEquals("current", context.queryType());
    }
}
