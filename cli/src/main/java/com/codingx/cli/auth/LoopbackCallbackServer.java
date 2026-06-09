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
     * 给浏览器返回登录结果页；页面本身不承载敏感 token，只告诉用户是否可回到终端。
     *
     * @param exchange HTTP 交换对象。
     * @param status HTTP 状态码。
     * @param message 页面提示。
     * @throws IOException 响应失败时抛出。
     */
    private void writeHtml(HttpExchange exchange, int status, String message) throws IOException {
        boolean success = status >= 200 && status < 300;
        String shellState = success ? "auth-shell--success" : "auth-shell--error";
        String title = success ? "CodingX CLI 已连接" : "CodingX CLI 授权未完成";
        String eyebrow = success ? "CONNECTED" : "ACTION REQUIRED";
        String guidance = success
            ? "可以关闭此页面，回到终端继续使用。"
            : "请关闭此页面，回到终端重新发起登录。";
        String body = """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>%s</title>
              <style>
                :root {
                  color-scheme: light;
                  --page-bg: #f6f3ed;
                  --panel-bg: #111315;
                  --panel-border: #252a2e;
                  --text-main: #f5f1e8;
                  --text-muted: #9ea7ad;
                  --paper: #fffaf1;
                  --paper-text: #25211b;
                  --accent: #24c08b;
                  --accent-soft: rgba(36, 192, 139, 0.14);
                  --error: #e15d4f;
                  --error-soft: rgba(225, 93, 79, 0.14);
                  --shadow: 0 28px 80px rgba(19, 22, 24, 0.18);
                }

                * {
                  box-sizing: border-box;
                }

                body {
                  margin: 0;
                  min-height: 100vh;
                  display: grid;
                  place-items: center;
                  background:
                    radial-gradient(circle at top left, rgba(36, 192, 139, 0.16), transparent 32rem),
                    linear-gradient(135deg, #fffaf1, var(--page-bg));
                  color: var(--paper-text);
                  font-family: ui-sans-serif, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                  padding: 24px;
                }

                .auth-shell {
                  width: min(680px, calc(100vw - 32px));
                  border: 1px solid var(--panel-border);
                  border-radius: 18px;
                  overflow: hidden;
                  background: var(--panel-bg);
                  color: var(--text-main);
                  box-shadow: var(--shadow);
                }

                .auth-shell__bar {
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  gap: 16px;
                  padding: 16px 18px;
                  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
                  background: rgba(255, 255, 255, 0.03);
                }

                .auth-shell__brand {
                  display: flex;
                  align-items: center;
                  gap: 10px;
                  font-size: 14px;
                  font-weight: 700;
                  letter-spacing: 0;
                }

                .auth-shell__prompt {
                  display: inline-grid;
                  place-items: center;
                  width: 28px;
                  height: 28px;
                  border-radius: 8px;
                  background: var(--accent-soft);
                  color: var(--accent);
                  font: 700 15px ui-monospace, SFMono-Regular, Consolas, monospace;
                }

                .auth-shell--error .auth-shell__prompt {
                  background: var(--error-soft);
                  color: var(--error);
                }

                .auth-shell__badge {
                  border: 1px solid rgba(36, 192, 139, 0.32);
                  border-radius: 999px;
                  padding: 6px 10px;
                  color: var(--accent);
                  background: var(--accent-soft);
                  font: 700 11px ui-monospace, SFMono-Regular, Consolas, monospace;
                  letter-spacing: 0.04em;
                }

                .auth-shell--error .auth-shell__badge {
                  border-color: rgba(225, 93, 79, 0.32);
                  color: var(--error);
                  background: var(--error-soft);
                }

                .auth-shell__content {
                  padding: 44px 44px 40px;
                }

                .auth-shell__eyebrow {
                  margin: 0 0 14px;
                  color: var(--accent);
                  font: 700 12px ui-monospace, SFMono-Regular, Consolas, monospace;
                  letter-spacing: 0.08em;
                }

                .auth-shell--error .auth-shell__eyebrow {
                  color: var(--error);
                }

                h1 {
                  margin: 0;
                  font-size: clamp(30px, 6vw, 54px);
                  line-height: 1.02;
                  letter-spacing: 0;
                }

                .auth-shell__message {
                  margin: 20px 0 0;
                  max-width: 520px;
                  color: var(--text-muted);
                  font-size: 16px;
                  line-height: 1.7;
                }

                .auth-shell__terminal {
                  margin-top: 30px;
                  border: 1px solid rgba(255, 255, 255, 0.09);
                  border-radius: 12px;
                  background: rgba(0, 0, 0, 0.22);
                  overflow: hidden;
                }

                .auth-shell__terminal-head {
                  display: flex;
                  align-items: center;
                  gap: 7px;
                  padding: 11px 13px;
                  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
                }

                .auth-shell__dot {
                  width: 9px;
                  height: 9px;
                  border-radius: 999px;
                  background: #575f66;
                }

                .auth-shell__terminal-line {
                  margin: 0;
                  padding: 18px;
                  color: var(--text-main);
                  font: 14px ui-monospace, SFMono-Regular, Consolas, monospace;
                  white-space: normal;
                }

                .auth-shell__terminal-line span {
                  color: var(--accent);
                }

                .auth-shell--error .auth-shell__terminal-line span {
                  color: var(--error);
                }

                .auth-shell__hint {
                  margin: 20px 0 0;
                  color: var(--text-muted);
                  font-size: 14px;
                }

                @media (prefers-color-scheme: dark) {
                  :root {
                    color-scheme: dark;
                    --page-bg: #0c0f11;
                    --paper-text: #f2ede3;
                    --shadow: 0 30px 90px rgba(0, 0, 0, 0.46);
                  }

                  body {
                    background:
                      radial-gradient(circle at top left, rgba(36, 192, 139, 0.12), transparent 30rem),
                      linear-gradient(135deg, #15100c, var(--page-bg));
                  }
                }

                @media (max-width: 560px) {
                  body {
                    padding: 16px;
                  }

                  .auth-shell {
                    width: calc(100vw - 24px);
                    border-radius: 14px;
                  }

                  .auth-shell__bar {
                    align-items: flex-start;
                    flex-direction: column;
                  }

                  .auth-shell__content {
                    padding: 34px 24px 28px;
                  }
                }
              </style>
            </head>
            <body>
              <main class="auth-shell %s">
                <div class="auth-shell__bar">
                  <div class="auth-shell__brand">
                    <span class="auth-shell__prompt">&gt;_</span>
                    <span>CodingX CLI</span>
                  </div>
                  <span class="auth-shell__badge">%s</span>
                </div>
                <section class="auth-shell__content" aria-label="CodingX CLI 登录结果">
                  <p class="auth-shell__eyebrow">%s</p>
                  <h1>%s</h1>
                  <p class="auth-shell__message">%s</p>
                  <div class="auth-shell__terminal" aria-hidden="true">
                    <div class="auth-shell__terminal-head">
                      <i class="auth-shell__dot"></i>
                      <i class="auth-shell__dot"></i>
                      <i class="auth-shell__dot"></i>
                    </div>
                    <p class="auth-shell__terminal-line"><span>&gt;</span> codingx</p>
                  </div>
                  <p class="auth-shell__hint">%s</p>
                </section>
              </main>
              </body>
            </html>
            """.formatted(
            title,
            shellState,
            eyebrow,
            eyebrow,
            title,
            escapeHtml(message),
            guidance
        );
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

