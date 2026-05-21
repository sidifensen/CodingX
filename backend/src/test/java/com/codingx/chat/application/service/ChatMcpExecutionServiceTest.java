package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.mcp.application.service.ChatMcpProgressListener;
import com.codingx.mcp.application.service.ChatMcpExecutionService;
import com.codingx.mcp.application.service.ChatMcpToolExecutor;
import com.codingx.mcp.application.service.ChatMcpToolRegistry;
import com.codingx.mcp.application.service.ChatMcpToolResult;
import com.codingx.mcp.application.service.WeatherMcpToolExecutor;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * 验证 MCP 执行服务会通过注册表分发到对应工具执行器。
 */
class ChatMcpExecutionServiceTest {

    /**
     * 天气工具应通过注册表分发到真实执行器并返回天气结果。
     */
    @Test
    void executeDispatchesToWeatherExecutor() {
        ChatMcpExecutionService service = new ChatMcpExecutionService(
            new ChatMcpToolRegistry(List.of(new WeatherMcpToolExecutor()))
        );

        ChatMcpToolResult result = service.execute("weather_query", "北京今天天气怎么样");

        assertEquals("weather_query", result.toolId());
        assertTrue(result.content().contains("天气"));
    }

    /**
     * 三参执行入口应把进度监听透传给工具执行器，确保真实进度可上报。
     */
    @Test
    void executeWithProgressListenerPassesThroughToExecutor() {
        AtomicBoolean progressObserved = new AtomicBoolean(false);
        ChatMcpToolExecutor progressExecutor = new ChatMcpToolExecutor() {
            @Override
            public String toolId() {
                return "test_progress";
            }

            @Override
            public ChatMcpToolResult execute(String question) {
                return new ChatMcpToolResult(toolId(), "fallback", java.util.Map.of());
            }

            @Override
            public ChatMcpToolResult execute(String question, ChatMcpProgressListener progressListener) {
                progressListener.onProgress("phase-a", "进度A", java.util.Map.of("question", question));
                return new ChatMcpToolResult(toolId(), "ok", java.util.Map.of());
            }
        };
        ChatMcpExecutionService service = new ChatMcpExecutionService(
            new ChatMcpToolRegistry(List.of(progressExecutor))
        );

        ChatMcpToolResult result = service.execute(
            "test_progress",
            "hello",
            (stage, message, detail) -> progressObserved.set("phase-a".equals(stage) && "进度A".equals(message))
        );

        assertEquals("test_progress", result.toolId());
        assertEquals("ok", result.content());
        assertTrue(progressObserved.get());
    }
}
