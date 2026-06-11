package com.codingx.cli.session;

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
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后端会话列表客户端测试，确保 CLI 直接复用 Web 会话接口而不是新增协议。
 */
class BackendConversationClientTest {

    @TempDir
    Path tempDir;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void listRecentConversationsShouldRequestWebConversationPage() throws Exception {
        CapturedRequest capturedRequest = new CapturedRequest();
        server = startConversationServer(capturedRequest, 200, """
            {"success":true,"code":"OK","message":"success","data":{"items":[
              {"id":"101","title":"修复登录","status":"ACTIVE","updatedAt":"2026-06-11T10:00:00","workspaceId":"9","workspaceType":"LOCAL"},
              {"id":"102","title":"聊天优化","status":"ACTIVE","lastMessageAt":"2026-06-11T09:00:00","workspaceId":null,"workspaceType":"CLOUD"}
            ],"hasMore":false,"nextCursor":null}}
            """);
        CliConfigStore configStore = saveConfig("token-123");

        BackendConversationClient client = new BackendConversationClient(configStore);
        List<CliConversation> conversations = client.listRecentConversations(20);

        assertEquals("/api/chat/conversations", capturedRequest.path);
        assertEquals("20", capturedRequest.query.get("pageSize"));
        assertEquals("token-123", capturedRequest.headers.get("satoken"));
        assertEquals(2, conversations.size());
        assertEquals("101", conversations.getFirst().id());
        assertEquals("修复登录", conversations.getFirst().title());
        assertEquals("LOCAL", conversations.getFirst().workspaceType());
        assertEquals("2026-06-11T09:00:00", conversations.get(1).updatedAt());
    }

    @Test
    void listRecentConversationsShouldParseLegacyArrayResponse() throws Exception {
        CapturedRequest capturedRequest = new CapturedRequest();
        server = startConversationServer(capturedRequest, 200, """
            {"success":true,"code":"OK","message":"success","data":[
              {"id":201,"title":"旧协议会话","status":"ACTIVE","lastMessageAt":"2026-06-11T08:00:00","workspaceType":"cloud"}
            ]}
            """);
        CliConfigStore configStore = saveConfig("token-123");

        BackendConversationClient client = new BackendConversationClient(configStore);
        List<CliConversation> conversations = client.listRecentConversations(10);

        assertEquals("10", capturedRequest.query.get("pageSize"));
        assertEquals(1, conversations.size());
        assertEquals("201", conversations.getFirst().id());
        assertEquals("旧协议会话", conversations.getFirst().title());
        assertEquals("cloud", conversations.getFirst().workspaceType());
    }

    @Test
    void listRecentConversationsShouldFailBeforeHttpWhenTokenMissing() {
        CliConfigStore configStore = saveConfig("");
        BackendConversationClient client = new BackendConversationClient(configStore);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.listRecentConversations(20)
        );

        assertTrue(exception.getMessage().contains("未登录或登录已失效"));
    }

    @Test
    void listRecentConversationsShouldUseBackendMessageOnFailure() throws Exception {
        CapturedRequest capturedRequest = new CapturedRequest();
        server = startConversationServer(capturedRequest, 200, """
            {"success":false,"code":"AUTH_EXPIRED","message":"登录已过期，请重新登录","data":null}
            """);
        CliConfigStore configStore = saveConfig("token-123");
        BackendConversationClient client = new BackendConversationClient(configStore);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.listRecentConversations(20)
        );

        assertEquals("登录已过期，请重新登录", exception.getMessage());
    }

    @Test
    void listRecentConversationsShouldUseBackendMessageOnHttpFailure() throws Exception {
        CapturedRequest capturedRequest = new CapturedRequest();
        server = startConversationServer(capturedRequest, 401, """
            {"success":false,"code":"AUTH_EXPIRED","message":"登录已过期，请重新登录","data":null}
            """);
        CliConfigStore configStore = saveConfig("token-123");
        BackendConversationClient client = new BackendConversationClient(configStore);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> client.listRecentConversations(20)
        );

        assertEquals("登录已过期，请重新登录", exception.getMessage());
    }

    /**
     * 保存测试配置，serverUrl 指向当前模拟 HTTP 服务。
     */
    private CliConfigStore saveConfig(String token) {
        CliConfigStore configStore = new CliConfigStore(tempDir.resolve("home"));
        String serverUrl = server == null
            ? "http://127.0.0.1:1"
            : "http://127.0.0.1:" + server.getAddress().getPort();
        configStore.save(new CliConfig(serverUrl, token, "conservative", null));
        return configStore;
    }

    /**
     * 启动会话列表接口模拟服务，记录请求并返回指定 JSON。
     */
    private HttpServer startConversationServer(CapturedRequest capturedRequest, int statusCode, String body) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/api/chat/conversations", exchange ->
            handleJson(exchange, capturedRequest, statusCode, body));
        httpServer.start();
        return httpServer;
    }

    /**
     * 记录请求并返回 JSON 响应。
     */
    private void handleJson(
        HttpExchange exchange,
        CapturedRequest capturedRequest,
        int statusCode,
        String body
    ) throws IOException {
        URI uri = exchange.getRequestURI();
        capturedRequest.path = uri.getPath();
        capturedRequest.query = parseQuery(uri.getRawQuery());
        capturedRequest.headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((key, values) -> {
            if (!values.isEmpty()) {
                capturedRequest.headers.put(key.toLowerCase(), values.getFirst());
            }
        });
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /**
     * 解析 URL 查询参数，便于断言分页参数。
     */
    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> query = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return query;
        }
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            query.put(key, value);
        }
        return query;
    }

    /**
     * 测试请求快照。
     */
    private static class CapturedRequest {
        /** 请求路径。 */
        private String path;
        /** 查询参数。 */
        private Map<String, String> query = new LinkedHashMap<>();
        /** 小写 header 到首个 header 值。 */
        private Map<String, String> headers = new LinkedHashMap<>();
    }
}
