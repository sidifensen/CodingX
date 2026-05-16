package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 验证天气 MCP 工具执行器的核心行为。
 */
class WeatherMcpToolExecutorTest {

    /**
     * 当前天气查询应返回今日天气标题。
     */
    @Test
    void executeBuildsCurrentWeather() {
        WeatherMcpToolExecutor executor = new WeatherMcpToolExecutor();

        ChatMcpToolResult result = executor.execute("北京今天天气怎么样");

        assertEquals("weather_query", result.toolId());
        assertTrue(result.content().contains("今日天气"));
    }

    /**
     * 预报查询应返回未来天气标题。
     */
    @Test
    void executeBuildsForecastWeather() {
        WeatherMcpToolExecutor executor = new WeatherMcpToolExecutor();

        ChatMcpToolResult result = executor.execute("上海未来三天天气预报");

        assertEquals("weather_query", result.toolId());
        assertTrue(result.content().contains("未来3天天气预报"));
    }
}
