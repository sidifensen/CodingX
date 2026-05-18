package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.mcp.application.service.ChatMcpToolResult;
import com.codingx.mcp.application.service.WeatherMcpToolExecutor;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * 验证天气 MCP 工具执行器的核心行为。
 */
class WeatherMcpToolExecutorTest {

    /**
     * 当前天气查询应按 Open-Meteo 数据返回今日天气摘要。
     */
    @Test
    void executeBuildsCurrentWeatherWithOpenMeteoPayload() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/geocoding", exchange -> {
                respondJson(
                    exchange,
                    """
                    {
                      "results": [
                        {"name":"北京","admin1":"北京","country":"中国","latitude":39.9042,"longitude":116.4074}
                      ]
                    }
                    """
                );
            });
            server.createContext("/forecast", exchange -> {
                respondJson(
                    exchange,
                    """
                    {
                      "timezone":"Asia/Shanghai",
                      "utc_offset_seconds":28800,
                      "current":{
                        "time":"2026-05-16T10:00",
                        "temperature_2m":26.5,
                        "apparent_temperature":27.0,
                        "weather_code":1,
                        "wind_speed_10m":12.3
                      },
                      "daily":{
                        "time":["2026-05-16","2026-05-17","2026-05-18"],
                        "weather_code":[1,2,61],
                        "temperature_2m_max":[28.0,29.5,24.1],
                        "temperature_2m_min":[19.2,20.1,17.8],
                        "precipitation_probability_max":[10,20,80],
                        "wind_speed_10m_max":[18.1,16.2,20.4]
                      }
                    }
                    """
                );
            });
            server.start();

            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            WeatherMcpToolExecutor executor = new WeatherMcpToolExecutor() {
                @Override
                protected String geocodingEndpoint() {
                    return baseUrl + "/geocoding";
                }

                @Override
                protected String forecastEndpoint() {
                    return baseUrl + "/forecast";
                }
            };

            ChatMcpToolResult result = executor.execute("北京今天天气怎么样");

            assertEquals("weather_query", result.toolId());
            assertTrue(result.content().contains("北京"));
            assertTrue(result.content().contains("当前温度: 26.5°C"));
            assertTrue(result.content().contains("数据源: Open-Meteo"));
        } finally {
            server.stop(0);
        }
    }

    /**
     * 当前天气查询应返回今日天气标题。
     */
    @Test
    void executeBuildsCurrentWeather() {
        WeatherMcpToolExecutor executor = new WeatherMcpToolExecutor();

        ChatMcpToolResult result = executor.execute("北京今天天气怎么样");

        assertEquals("weather_query", result.toolId());
        assertTrue(
            result.content().contains("今日天气") || result.content().contains("天气服务暂时不可用"),
            "天气接口异常时应返回友好降级提示"
        );
    }

    /**
     * 预报查询应返回未来天气标题，且在接口异常时返回友好降级提示。
     */
    @Test
    void executeBuildsForecastWeather() {
        WeatherMcpToolExecutor executor = new WeatherMcpToolExecutor();

        ChatMcpToolResult result = executor.execute("上海未来三天天气预报");

        assertEquals("weather_query", result.toolId());
        assertTrue(
            result.content().contains("未来3天天气预报") || result.content().contains("天气服务暂时不可用"),
            "天气接口异常时应返回友好降级提示"
        );
    }

    /**
     * 响应 JSON 文本给测试 HTTP 服务器。
     * @param exchange HTTP 请求上下文。
     * @param body 响应 JSON。
     * @throws IOException 写响应异常。
     */
    private static void respondJson(HttpExchange exchange, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }
}
