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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
            List<String> progressEvents = new ArrayList<>();

            ChatMcpToolResult result = executor.execute(
                "北京今天天气怎么样",
                (stage, message, detail) -> progressEvents.add(stage + ":" + message)
            );

            assertEquals("weather_query", result.toolId());
            assertTrue(result.content().contains("北京"));
            assertTrue(result.content().contains("当前温度: 26.5°C"));
            assertTrue(result.content().contains("数据源: Open-Meteo"));
            assertTrue(progressEvents.stream().anyMatch(item -> item.startsWith("parse-question:")));
            assertTrue(progressEvents.stream().anyMatch(item -> item.startsWith("resolve-coordinates:")));
            assertTrue(progressEvents.stream().anyMatch(item -> item.startsWith("query-forecast:")));
            assertTrue(progressEvents.stream().anyMatch(item -> item.startsWith("build-result:")));
            assertTrue(progressEvents.stream().anyMatch(item -> item.startsWith("completed:")));
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
     * 城市助词（如“的”）应在坐标查询前剔除，避免命中不到地理编码结果。
     */
    @Test
    void executeNormalizesCityWhenQuestionContainsPossessiveParticle() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/geocoding", exchange -> {
                String decodedQuery = decodeQuery(exchange.getRequestURI().getRawQuery());
                // 仅当工具把城市参数规范为“北京”时返回坐标，确保回归测试能覆盖该场景。
                if (decodedQuery.contains("name=北京&") || decodedQuery.endsWith("name=北京")) {
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
                    return;
                }
                respondJson(exchange, "{\"results\":[]}");
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

            ChatMcpToolResult result = executor.execute("北京的天气怎么样");

            assertEquals("weather_query", result.toolId());
            assertTrue(result.content().contains("【北京 今日天气】"), "城市助词应在解析阶段被剔除");
        } finally {
            server.stop(0);
        }
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

    /**
     * 解码 URL 查询参数，便于断言中文城市名是否被正确透传。
     *
     * @param rawQuery 原始查询字符串。
     * @return UTF-8 解码后的查询字符串。
     */
    private static String decodeQuery(String rawQuery) {
        if (rawQuery == null) {
            return "";
        }
        return URLDecoder.decode(rawQuery, StandardCharsets.UTF_8);
    }
}
