package com.codingx.cli.backend;

import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.AgentEventType;
import com.codingx.cli.config.CliConfig;
import com.codingx.cli.config.CliConfigStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后端聊天流事件源测试，通过本地 HTTP 服务模拟现有 `/api/chat/stream` SSE 协议。
 */
class BackendChatEventSourceTest {

    /**
     * 临时目录同时承载测试用户主目录和工作区，避免读写真实 `~/.codingx`。
     */
    @TempDir
    Path tempDir;

    /**
     * 测试期间启动的本地 HTTP 服务。
     */
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void startTurnShouldRequestBackendStreamWithTokenLocalRuntimeAndConversation() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
        CliConfigStore configStore = new CliConfigStore(tempDir.resolve("home"));
        CapturedRequest capturedRequest = new CapturedRequest();
        server = startServer(capturedRequest, 200, sse(
            block("meta", "{\"conversationId\":67890,\"runId\":777,\"taskId\":777}"),
            block("message", "{\"type\":\"response\",\"delta\":\"后端回复\"}"),
            block("thinking", "{\"type\":\"thinking\",\"delta\":\"正在分析\"}"),
            block("tool-call", "{\"phase\":\"start\",\"toolId\":\"grep\",\"displayName\":\"代码搜索\"}"),
            block("tool-call", "{\"phase\":\"complete\",\"toolId\":\"grep\",\"displayName\":\"代码搜索\",\"rawResult\":\"命中 2 处\"}"),
            block("finish", "{\"conversationId\":67890,\"content\":\"后端回复\",\"title\":\"CLI 会话\"}"),
            block("done", "{\"conversationId\":67890}")
        ));
        configStore.save(new CliConfig(baseUrl(), "token-123", "conservative", "12345"));

        BackendChatEventSource eventSource = new BackendChatEventSource(configStore);
        List<AgentEvent> events = eventSource.startTurn("分析这个项目", workspace);

        assertEquals("/api/chat/stream", capturedRequest.path);
        assertEquals("GET", capturedRequest.method);
        assertEquals("token-123", capturedRequest.headers.get("satoken"));
        assertEquals("分析这个项目", capturedRequest.query.get("question"));
        assertEquals("local", capturedRequest.query.get("runtimeTarget"));
        assertEquals(workspace.toAbsolutePath().normalize().toString(), capturedRequest.query.get("repositoryPath"));
        assertEquals("12345", capturedRequest.query.get("conversationId"));
        assertEquals("false", capturedRequest.query.get("planMode"));

        assertTrue(events.stream().anyMatch(event -> event.eventType() == AgentEventType.TURN_STARTED));
        assertTrue(events.stream().anyMatch(event -> event.eventType() == AgentEventType.ASSISTANT_DELTA
            && "后端回复".equals(event.payloadText("delta"))));
        assertTrue(events.stream().anyMatch(event -> event.eventType() == AgentEventType.THINKING_DELTA
            && "正在分析".equals(event.payloadText("delta"))));
        assertTrue(events.stream().anyMatch(event -> event.eventType() == AgentEventType.TOOL_STARTED
            && "代码搜索".equals(event.payloadText("displayName"))));
        assertTrue(events.stream().anyMatch(event -> event.eventType() == AgentEventType.TOOL_COMPLETED
            && "命中 2 处".equals(event.payloadText("rawResult"))));
        assertTrue(events.stream().anyMatch(event -> event.eventType() == AgentEventType.TURN_COMPLETED
            && "COMPLETED".equals(event.payloadText("status"))));
        assertEquals("67890", configStore.load().lastSessionId());
    }

    @Test
    void startTurnShouldIgnoreNonNumericLastSessionIdForLocalRuntime() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
        CliConfigStore configStore = new CliConfigStore(tempDir.resolve("home"));
        CapturedRequest capturedRequest = new CapturedRequest();
        server = startServer(capturedRequest, 200, sse(
            block("meta", "{\"conversationId\":67890}"),
            block("done", "{\"conversationId\":67890}")
        ));
        configStore.save(new CliConfig(baseUrl(), "token-123", "conservative", "local-demo"));

        BackendChatEventSource eventSource = new BackendChatEventSource(configStore);
        eventSource.startTurn("继续分析", workspace);

        assertFalse(capturedRequest.query.containsKey("conversationId"));
    }

    @Test
    void startTurnShouldPreferBackendApiResponseMessageForNonSuccessStatus() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
        CliConfigStore configStore = new CliConfigStore(tempDir.resolve("home"));
        CapturedRequest capturedRequest = new CapturedRequest();
        server = startServer(capturedRequest, 401, "{\"code\":\"AUTH_REQUIRED\",\"message\":\"请先登录\"}");
        configStore.save(new CliConfig(baseUrl(), "expired-token", "conservative", null));

        BackendChatEventSource eventSource = new BackendChatEventSource(configStore);
        List<AgentEvent> events = eventSource.startTurn("分析登录态", workspace);

        assertEquals("expired-token", capturedRequest.headers.get("satoken"));
        assertTrue(capturedRequest.headers.get("accept").contains("application/json"));
        assertTrue(events.stream().anyMatch(event -> event.eventType() == AgentEventType.ERROR
            && "请先登录".equals(event.payloadText("message"))));
    }

    @Test
    void startTurnShouldSendPlanModeQueryParam() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
        CliConfigStore configStore = new CliConfigStore(tempDir.resolve("home"));
        CapturedRequest capturedRequest = new CapturedRequest();
        server = startServer(capturedRequest, 200, sse(block("done", "{\"conversationId\":67890}")));
        configStore.save(new CliConfig(baseUrl(), "token-123", "conservative", null));

        BackendChatEventSource eventSource = new BackendChatEventSource(configStore);
        eventSource.startTurn("先规划实现步骤", workspace, true, ignored -> {
        });

        assertEquals("true", capturedRequest.query.get("planMode"));
    }

    /**
     * 启动测试用 HTTP 服务；handler 记录请求后按指定状态码和正文返回。
     *
     * @param capturedRequest 请求记录容器。
     * @param statusCode HTTP 状态码。
     * @param body 响应正文。
     * @return 已启动服务。
     */
    private HttpServer startServer(CapturedRequest capturedRequest, int statusCode, String body) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/api/chat/stream", exchange -> handleStream(exchange, capturedRequest, statusCode, body));
        httpServer.start();
        return httpServer;
    }

    /**
     * 记录请求并返回模拟响应，SSE 成功响应带 `text/event-stream` 类型。
     */
    private void handleStream(
        HttpExchange exchange,
        CapturedRequest capturedRequest,
        int statusCode,
        String body
    ) throws IOException {
        URI uri = exchange.getRequestURI();
        capturedRequest.method = exchange.getRequestMethod();
        capturedRequest.path = uri.getPath();
        capturedRequest.query = parseQuery(uri.getRawQuery());
        capturedRequest.headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((key, values) -> {
            if (!values.isEmpty()) {
                capturedRequest.headers.put(key.toLowerCase(), values.getFirst());
            }
        });
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (statusCode == 200) {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream;charset=UTF-8");
        } else {
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        }
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /**
     * 解析查询参数并保持 URL 解码后的真实值，便于断言中文问题和 Windows 路径。
     */
    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return values;
        }
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return values;
    }

    /**
     * @return 当前测试服务的根地址。
     */
    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * 拼接多个 SSE 事件块。
     */
    private String sse(String... blocks) {
        return String.join("", blocks);
    }

    /**
     * 构造标准 SSE 事件块，使用空行作为块边界。
     */
    private String block(String eventName, String data) {
        return "event: " + eventName + "\n"
            + "data: " + data + "\n\n";
    }

    /**
     * 保存测试服务收到的最近一次请求。
     */
    private static class CapturedRequest {

        /**
         * HTTP 方法。
         */
        private String method;

        /**
         * 请求路径。
         */
        private String path;

        /**
         * 查询参数。
         */
        private Map<String, String> query = new LinkedHashMap<>();

        /**
         * 小写 header 到首个 header 值的映射。
         */
        private Map<String, String> headers = new LinkedHashMap<>();
    }
}
