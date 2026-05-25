package com.codingx.tool.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.common.exception.BusinessException;
import com.codingx.config.WebAccessProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 验证 web_access 工具能真实代理到本机 CDP Proxy，而不是返回数据库占位信息。
 */
class WebAccessChatToolExecutorTest {

    /**
     * web_access 应支持列出标签页、打开新页、执行脚本、截图与关闭等核心 CDP 动作。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void webAccessShouldProxyCommonCdpActions(@TempDir Path tempDir) throws Exception {
        Map<String, AtomicReference<String>> captured = new LinkedHashMap<>();
        captured.put("targetsMethod", new AtomicReference<>());
        captured.put("newUrl", new AtomicReference<>());
        captured.put("evalTarget", new AtomicReference<>());
        captured.put("evalBody", new AtomicReference<>());
        captured.put("clickTarget", new AtomicReference<>());
        captured.put("clickBody", new AtomicReference<>());
        captured.put("setFilesTarget", new AtomicReference<>());
        captured.put("setFilesBody", new AtomicReference<>());
        captured.put("scrollTarget", new AtomicReference<>());
        captured.put("scrollDirection", new AtomicReference<>());
        captured.put("closeTarget", new AtomicReference<>());

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/targets", exchange -> {
                captured.get("targetsMethod").set(exchange.getRequestMethod());
                respondJson(exchange, null, "[{\"id\":\"tab-1\"}]");
            });
            server.createContext("/new", exchange -> {
                captured.get("newUrl").set(queryParam(exchange.getRequestURI(), "url"));
                respondJson(exchange, null, "{\"target\":\"tab-new\"}");
            });
            server.createContext("/eval", exchange -> {
                captured.get("evalTarget").set(queryParam(exchange.getRequestURI(), "target"));
                captured.get("evalBody").set(readBody(exchange));
                respondJson(exchange, null, "{\"value\":\"CodingX\"}");
            });
            server.createContext("/screenshot", exchange -> {
                String filePath = queryParam(exchange.getRequestURI(), "file");
                if (filePath != null && !filePath.isBlank()) {
                    Files.writeString(Path.of(filePath), "png-bytes", StandardCharsets.UTF_8);
                }
                respondJson(exchange, null, "{\"saved\":true}");
            });
            server.createContext("/click", exchange -> {
                captured.get("clickTarget").set(queryParam(exchange.getRequestURI(), "target"));
                captured.get("clickBody").set(readBody(exchange));
                respondJson(exchange, null, "{\"clicked\":true}");
            });
            server.createContext("/setFiles", exchange -> {
                captured.get("setFilesTarget").set(queryParam(exchange.getRequestURI(), "target"));
                captured.get("setFilesBody").set(readBody(exchange));
                respondJson(exchange, null, "{\"uploaded\":true}");
            });
            server.createContext("/scroll", exchange -> {
                captured.get("scrollTarget").set(queryParam(exchange.getRequestURI(), "target"));
                captured.get("scrollDirection").set(queryParam(exchange.getRequestURI(), "direction"));
                respondJson(exchange, null, "{\"scrolled\":true}");
            });
            server.createContext("/close", exchange -> {
                captured.get("closeTarget").set(queryParam(exchange.getRequestURI(), "target"));
                respondJson(exchange, null, "{\"closed\":true}");
            });
            server.start();

            WebAccessProperties properties = new WebAccessProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            WebAccessChatToolExecutor executor = new WebAccessChatToolExecutor(properties);

            ChatToolExecutionResult targetsResult = executor.execute("web_access", "{\"action\":\"targets\"}");
            assertTrue(targetsResult.content().contains("tab-1"));
            assertEquals("GET", captured.get("targetsMethod").get());

            ChatToolExecutionResult newResult = executor.execute("web_access", "{\"action\":\"new\",\"url\":\"https://example.com\"}");
            assertTrue(newResult.content().contains("tab-new"));
            assertEquals("https://example.com", captured.get("newUrl").get());

            ChatToolExecutionResult evalResult = executor.execute(
                "web_access",
                "{\"action\":\"eval\",\"target\":\"tab-new\",\"script\":\"document.title\"}"
            );
            assertTrue(evalResult.content().contains("CodingX"));
            assertEquals("tab-new", captured.get("evalTarget").get());
            assertEquals("document.title", captured.get("evalBody").get());

            ChatToolExecutionResult screenshotResult = executor.execute(
                "web_access",
                "{\"action\":\"screenshot\",\"target\":\"tab-new\"}"
            );
            String screenshotPath = String.valueOf(screenshotResult.metadata().get("file"));
            assertTrue(Files.exists(Path.of(screenshotPath)));

            ChatToolExecutionResult clickResult = executor.execute(
                "web_access",
                "{\"action\":\"click\",\"target\":\"tab-new\",\"selector\":\"button.submit\"}"
            );
            assertTrue(clickResult.content().contains("clicked"));
            assertEquals("tab-new", captured.get("clickTarget").get());
            assertEquals("button.submit", captured.get("clickBody").get());

            ChatToolExecutionResult setFilesResult = executor.execute(
                "web_access",
                """
                {"action":"setFiles","target":"tab-new","selector":"input[type=file]","files":["%s"]}
                """.formatted(tempDir.resolve("upload.txt").toString().replace("\\", "\\\\"))
            );
            assertTrue(setFilesResult.content().contains("uploaded"));
            assertEquals("tab-new", captured.get("setFilesTarget").get());
            assertTrue(captured.get("setFilesBody").get().contains("input[type=file]"));

            ChatToolExecutionResult scrollResult = executor.execute(
                "web_access",
                "{\"action\":\"scroll\",\"target\":\"tab-new\",\"direction\":\"bottom\"}"
            );
            assertTrue(scrollResult.content().contains("scrolled"));
            assertEquals("bottom", captured.get("scrollDirection").get());

            ChatToolExecutionResult closeResult = executor.execute("web_access", "{\"action\":\"close\",\"target\":\"tab-new\"}");
            assertTrue(closeResult.content().contains("closed"));
            assertEquals("tab-new", captured.get("closeTarget").get());
        } finally {
            server.stop(0);
        }
    }

    /**
     * 当 CDP Proxy 不可达时，工具应给出明确中文错误，而不是沉默失败。
     */
    @Test
    void webAccessShouldFailClearlyWhenProxyIsUnavailable() {
        WebAccessProperties properties = new WebAccessProperties();
        properties.setBaseUrl("http://127.0.0.1:65530");
        WebAccessChatToolExecutor executor = new WebAccessChatToolExecutor(properties);

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
            BusinessException.class,
            () -> executor.execute("web_access", "{\"action\":\"targets\"}")
        );

        assertFalse(exception.getMessage().isBlank());
        assertTrue(exception.getMessage().contains("web_access"));
    }

    private static void respondJson(HttpExchange exchange, AtomicReference<String> methodCapture, String body) throws IOException {
        respondJson(exchange, methodCapture, 200, body);
    }

    private static void respondJson(HttpExchange exchange, AtomicReference<String> methodCapture, int statusCode, String body) throws IOException {
        if (methodCapture != null) {
            methodCapture.set(exchange.getRequestMethod());
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String queryParam(URI uri, String name) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] fragments = pair.split("=", 2);
            if (fragments.length == 2 && fragments[0].equals(name)) {
                return java.net.URLDecoder.decode(fragments[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
