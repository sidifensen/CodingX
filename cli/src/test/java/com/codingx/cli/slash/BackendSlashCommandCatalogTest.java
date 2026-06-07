package com.codingx.cli.slash;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后端 Slash Command 目录客户端测试，确保 CLI 不硬编码管理端命令数据。
 */
class BackendSlashCommandCatalogTest {

    /**
     * 临时用户主目录，避免测试读写真实 `~/.codingx`。
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
    void listCommandsShouldRequestUserSlashCommandEndpointWithToken() throws Exception {
        CliConfigStore configStore = new CliConfigStore(tempDir.resolve("home"));
        CapturedRequest capturedRequest = new CapturedRequest();
        server = startServer(capturedRequest, 200, """
            {
              "success": true,
              "code": "OK",
              "message": "success",
              "data": [
                {
                  "id": 206060101,
                  "commandCode": "review",
                  "displayName": "/review",
                  "description": "审查当前改动并优先指出风险和测试缺口",
                  "commandType": "BUILTIN",
                  "sortNo": 1
                }
              ]
            }
            """);
        configStore.save(new CliConfig(baseUrl(), "token-123", "conservative", null));

        BackendSlashCommandCatalog catalog = new BackendSlashCommandCatalog(configStore);
        List<CliSlashCommand> commands = catalog.listCommands();

        assertEquals("/api/chat/slash-commands", capturedRequest.path);
        assertEquals("GET", capturedRequest.method);
        assertEquals("token-123", capturedRequest.headers.get("satoken"));
        assertEquals(1, commands.size());
        assertEquals("206060101", commands.getFirst().id());
        assertEquals("review", commands.getFirst().commandCode());
        assertEquals("/review", commands.getFirst().displayName());
        assertEquals("BUILTIN", commands.getFirst().commandType());
    }

    @Test
    void listCommandsShouldReturnEmptyWhenTokenMissingOrBackendFails() throws Exception {
        CliConfigStore missingTokenStore = new CliConfigStore(tempDir.resolve("missing-token-home"));
        missingTokenStore.save(new CliConfig("http://127.0.0.1:1", "", "conservative", null));
        assertTrue(new BackendSlashCommandCatalog(missingTokenStore).listCommands().isEmpty());

        CliConfigStore failedStore = new CliConfigStore(tempDir.resolve("failed-home"));
        CapturedRequest capturedRequest = new CapturedRequest();
        server = startServer(capturedRequest, 500, "{\"success\":false,\"message\":\"失败\"}");
        failedStore.save(new CliConfig(baseUrl(), "token-123", "conservative", null));

        assertTrue(new BackendSlashCommandCatalog(failedStore).listCommands().isEmpty());
        assertEquals("/api/chat/slash-commands", capturedRequest.path);
    }

    /**
     * 启动测试用 HTTP 服务；handler 记录请求后按指定状态码和正文返回。
     */
    private HttpServer startServer(CapturedRequest capturedRequest, int statusCode, String body) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/api/chat/slash-commands", exchange ->
            handleSlashCommands(exchange, capturedRequest, statusCode, body));
        httpServer.start();
        return httpServer;
    }

    /**
     * 记录请求并返回模拟 ApiResponse。
     */
    private void handleSlashCommands(
        HttpExchange exchange,
        CapturedRequest capturedRequest,
        int statusCode,
        String body
    ) throws IOException {
        URI uri = exchange.getRequestURI();
        capturedRequest.method = exchange.getRequestMethod();
        capturedRequest.path = uri.getPath();
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
     * @return 当前测试服务的根地址。
     */
    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
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
         * 小写 header 到首个 header 值的映射。
         */
        private Map<String, String> headers = new LinkedHashMap<>();
    }
}
