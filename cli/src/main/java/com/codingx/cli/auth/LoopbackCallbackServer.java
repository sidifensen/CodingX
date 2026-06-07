package com.codingx.cli.auth;

import cn.hutool.core.util.StrUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * CLI 浏览器登录本机回调服务，只监听 127.0.0.1 并接收一次 `/callback` 请求。
 */
public class LoopbackCallbackServer implements AutoCloseable {

    /**
     * CLI 登录启动时生成的 state，回调必须匹配。
     */
    private final String expectedState;

    /**
     * JDK 内置 HTTP 服务。
     */
    private final HttpServer server;

    /**
     * 回调结果 future，CLI 登录流程等待它取得 code。
     */
    private final CompletableFuture<CallbackResult> callbackResult = new CompletableFuture<>();

    /**
     * @param expectedState CLI 生成的 state。
     * @param server 已启动的 HTTP 服务。
     */
    private LoopbackCallbackServer(String expectedState, HttpServer server) {
        this.expectedState = expectedState;
        this.server = server;
    }

    /**
     * 启动本机 loopback 回调服务。
     *
     * @param expectedState CLI 生成的 state。
     * @return 回调服务。
     * @throws IOException 服务绑定失败时抛出。
     */
    public static LoopbackCallbackServer start(String expectedState) throws IOException {
        // 步骤 1：只绑定 127.0.0.1 和随机端口，避免局域网其他机器访问回调服务。
        HttpServer httpServer = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0),
            0
        );
        LoopbackCallbackServer callbackServer = new LoopbackCallbackServer(expectedState, httpServer);
        // 步骤 2：只暴露 `/callback`，其他路径不承载 CLI 授权结果。
        httpServer.createContext("/callback", callbackServer::handleCallback);
        httpServer.start();
        return callbackServer;
    }

    /**
     * @return CLI 传给浏览器的回调地址。
     */
    public URI callbackUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/callback");
    }

    /**
     * @return 等待浏览器回调的 future。
     */
    public CompletableFuture<CallbackResult> awaitCallback() {
        return callbackResult;
    }

    @Override
    public void close() {
        // 步骤：登录完成或超时后关闭临时服务，释放随机端口。
        server.stop(0);
    }

    /**
     * 处理浏览器跳回来的 callback。
     *
     * @param exchange HTTP 交换对象。
     * @throws IOException 响应写入失败时抛出。
     */
    private void handleCallback(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String code = query.get("code");
        String state = query.get("state");
        if (!StrUtil.equals(expectedState, state)) {
            writeHtml(exchange, 400, "状态校验失败，请回到终端重新登录");
            return;
        }
        if (StrUtil.isBlank(code)) {
            writeHtml(exchange, 400, "缺少授权码，请回到终端重新登录");
            return;
        }
        callbackResult.complete(new CallbackResult(code.trim(), state));
        writeHtml(exchange, 200, "授权完成，可以回到终端继续使用 CodingX CLI");
    }

    /**
     * 解析 URL 查询参数。
     *
     * @param rawQuery 原始查询串。
     * @return 解码后的参数。
     */
    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (StrUtil.isBlank(rawQuery)) {
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
     * 给浏览器返回简洁 HTML 页面。
     *
     * @param exchange HTTP 交换对象。
     * @param status HTTP 状态码。
     * @param message 页面提示。
     * @throws IOException 响应失败时抛出。
     */
    private void writeHtml(HttpExchange exchange, int status, String message) throws IOException {
        String body = """
            <!doctype html>
            <html lang="zh-CN">
              <meta charset="utf-8">
              <title>CodingX CLI</title>
              <body style="font-family: system-ui, sans-serif; padding: 32px;">
                <h1>%s</h1>
              </body>
            </html>
            """.formatted(escapeHtml(message));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html;charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /**
     * 最小 HTML 转义，避免异常参数污染回调页面。
     */
    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * 浏览器回调结果。
     *
     * @param code 一次性授权码。
     * @param state 已校验通过的 state。
     */
    public record CallbackResult(
        String code, // 一次性授权码。
        String state // CLI state。
    ) {
    }
}

