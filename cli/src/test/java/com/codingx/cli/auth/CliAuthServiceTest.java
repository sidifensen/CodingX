package com.codingx.cli.auth;

import com.codingx.cli.config.CliConfig;
import com.codingx.cli.config.CliConfigStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CLI 登录服务测试，用本地 HTTP 服务模拟后端授权接口，避免启动真实 Spring Boot。
 */
class CliAuthServiceTest {

    /**
     * 测试用户主目录，确保 token 不写入真实用户配置。
     */
    @TempDir
    Path tempDir;

    /**
     * 模拟后端服务。
     */
    private HttpServer backendServer;

    @AfterEach
    void stopServer() {
        if (backendServer != null) {
            backendServer.stop(0);
        }
    }

    /**
     * 浏览器登录主路径应打开 /cli-login，接收 loopback code，换取 token 并写入用户配置。
     */
    @Test
    void loginWithBrowserShouldExchangeCallbackCodeAndSaveToken() throws Exception {
        CapturedBackendRequests captured = new CapturedBackendRequests();
        backendServer = startBackend(captured);
        CliConfigStore configStore = new CliConfigStore(tempDir.resolve("home"));
        configStore.save(new CliConfig(baseUrl(), "", "conservative", null));
        RecordingBrowserLauncher browserLauncher = new RecordingBrowserLauncher();
        CliAuthService service = new CliAuthService(configStore, browserLauncher, message -> {
        });

        boolean loggedIn = service.loginWithBrowser();

        assertTrue(loggedIn);
        assertTrue(browserLauncher.openedUrl.contains("/cli-login?"));
        assertTrue(browserLauncher.openedUrl.contains("redirectUri="));
        assertEquals("/api/auth/cli/token", captured.paths.getLast());
        assertTrue(captured.lastTokenExchangeBody.contains("\"code\":\"cli-code-1\""));
        assertTrue(captured.lastTokenExchangeBody.contains("\"state\":\""));
        assertTrue(captured.lastTokenExchangeBody.contains("\"codeVerifier\":\""));
        assertEquals("cli-token-1", configStore.load().token());
        assertEquals(baseUrl(), configStore.load().serverUrl());
    }

    /**
     * 设备码登录应展示 userCode，pending 时继续轮询，approved 后保存 token。
     */
    @Test
    void loginWithDeviceCodeShouldPollUntilApprovedAndSaveToken() throws Exception {
        CapturedBackendRequests captured = new CapturedBackendRequests();
        backendServer = startBackend(captured);
        CliConfigStore configStore = new CliConfigStore(tempDir.resolve("home"));
        configStore.save(new CliConfig(baseUrl(), "", "conservative", null));
        List<String> messages = new ArrayList<>();
        CliAuthService service = new CliAuthService(configStore, url -> {
        }, messages::add);

        boolean loggedIn = service.loginWithDeviceCode();

        assertTrue(loggedIn);
        assertTrue(messages.stream().anyMatch(message -> message.contains("ABCD-EFGH")));
        assertEquals(2, captured.deviceTokenPollCount);
        assertEquals("device-token-1", configStore.load().token());
    }

    /**
     * 本机回调服务只接受匹配 state 的 callback，并把 code 交给等待中的 CLI 登录流程。
     */
    @Test
    void loopbackCallbackServerShouldRequireMatchingState() throws Exception {
        try (LoopbackCallbackServer callbackServer = LoopbackCallbackServer.start("state-expected")) {
            URI wrongUri = callbackServer.callbackUri();
            String wrongResponse = httpGet(wrongUri + "?code=bad&state=wrong");
            CompletableFuture<LoopbackCallbackServer.CallbackResult> resultFuture = callbackServer.awaitCallback();

            assertTrue(wrongResponse.contains("状态校验失败"));
            assertFalse(resultFuture.isDone());

            String successResponse = httpGet(wrongUri + "?code=cli-code-1&state=state-expected");

            assertTrue(successResponse.contains("授权完成"));
            assertTrue(successResponse.contains("CodingX CLI 已连接"));
            assertTrue(successResponse.contains("auth-shell"));
            assertTrue(successResponse.contains("@media (prefers-color-scheme: dark)"));
            assertTrue(successResponse.contains("可以关闭此页面"));
            assertEquals("cli-code-1", resultFuture.get().code());
        }
    }

    /**
     * 启动模拟后端。
     */
    private HttpServer startBackend(CapturedBackendRequests captured) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/cli/token", exchange -> handleTokenExchange(exchange, captured));
        server.createContext("/api/auth/cli/device/start", exchange -> handleDeviceStart(exchange, captured));
        server.createContext("/api/auth/cli/device/token", exchange -> handleDeviceToken(exchange, captured));
        server.start();
        return server;
    }

    /**
     * 模拟授权码 token 兑换，响应 CLI 登录 token。
     */
    private void handleTokenExchange(HttpExchange exchange, CapturedBackendRequests captured) throws IOException {
        captured.paths.add(exchange.getRequestURI().getPath());
        captured.lastTokenExchangeBody = readBody(exchange);
        writeJson(exchange, 200, """
            {"success":true,"code":"OK","message":"success","data":{"userId":1002,"username":"demo","displayName":"演示用户","userType":"USER","token":"cli-token-1"}}
            """);
    }

    /**
     * 模拟设备码启动响应。
     */
    private void handleDeviceStart(HttpExchange exchange, CapturedBackendRequests captured) throws IOException {
        captured.paths.add(exchange.getRequestURI().getPath());
        writeJson(exchange, 200, """
            {"success":true,"code":"OK","message":"success","data":{"deviceCode":"device-1","userCode":"ABCD-EFGH","verificationUri":"/cli-login","expiresInSeconds":600,"pollIntervalSeconds":0}}
            """);
    }

    /**
     * 第一次设备码轮询 pending，第二次 approved。
     */
    private void handleDeviceToken(HttpExchange exchange, CapturedBackendRequests captured) throws IOException {
        captured.paths.add(exchange.getRequestURI().getPath());
        captured.deviceTokenPollCount++;
        if (captured.deviceTokenPollCount == 1) {
            writeJson(exchange, 200, """
                {"success":true,"code":"OK","message":"success","data":{"approved":false,"status":"authorization_pending","login":null}}
                """);
            return;
        }
        writeJson(exchange, 200, """
            {"success":true,"code":"OK","message":"success","data":{"approved":true,"status":"approved","login":{"userId":1002,"username":"demo","displayName":"演示用户","userType":"USER","token":"device-token-1"}}}
            """);
    }

    /**
     * 读取请求体。
     */
    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * 返回 JSON 响应。
     */
    private void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /**
     * 发起本地 GET 请求。
     */
    private String httpGet(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        InputStream inputStream = connection.getResponseCode() >= 400
            ? connection.getErrorStream()
            : connection.getInputStream();
        try (inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * @return 模拟后端根地址。
     */
    private String baseUrl() {
        return "http://127.0.0.1:" + backendServer.getAddress().getPort();
    }

    /**
     * 记录浏览器打开的 URL，并模拟浏览器完成 loopback 回调。
     */
    private static class RecordingBrowserLauncher implements BrowserLauncher {

        /**
         * CLI 要打开的前端登录页。
         */
        private String openedUrl;

        @Override
        public void open(String url) throws IOException {
            openedUrl = url;
            Map<String, String> query = parseQuery(URI.create(url).getRawQuery());
            String callback = query.get("redirectUri") + "?code=cli-code-1&state=" + query.get("state");
            URI.create(callback).toURL().openStream().close();
        }

        /**
         * 解析前端登录 URL 查询参数。
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
    }

    /**
     * 保存模拟后端收到的请求。
     */
    private static class CapturedBackendRequests {

        /**
         * 请求路径序列。
         */
        private final List<String> paths = new ArrayList<>();

        /**
         * 最近一次授权码兑换请求体。
         */
        private String lastTokenExchangeBody;

        /**
         * 设备码 token 轮询次数。
         */
        private int deviceTokenPollCount;
    }
}
