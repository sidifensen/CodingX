package com.codingx.cli;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 CLI 进程级终端验收测试，避免 Web 会话命令只停留在内部 runner 单元测试。
 */
class CodingXCliTerminalTest {

    /**
     * 子进程等待秒数，防止异常命令卡住 Maven 测试。
     */
    private static final int PROCESS_TIMEOUT_SECONDS = 10;

    /**
     * 测试用户主目录，隔离 `~/.codingx/cli.yml`。
     */
    @TempDir
    Path tempDir;

    /**
     * 模拟 Web 后端会话接口。
     */
    private HttpServer backendServer;

    @AfterEach
    void stopServer() {
        if (backendServer != null) {
            backendServer.stop(0);
        }
    }

    /**
     * 终端主链路应能完成 login、sessions 和 resume，并把 Web 会话 ID 写入本机配置。
     */
    @Test
    void terminalShouldLoginListAndResumeWebConversation() throws Exception {
        CapturedConversationRequest captured = new CapturedConversationRequest();
        backendServer = startConversationServer(captured, 200, """
            {"success":true,"code":"OK","message":"success","data":{"items":[
              {"id":"101","title":"终端验收会话","status":"ACTIVE","updatedAt":"2026-06-11T12:00:00","workspaceId":"9","workspaceType":"LOCAL"}
            ],"hasMore":false,"nextCursor":null}}
            """);
        Path userHome = tempDir.resolve("home");

        ProcessResult login = runCli(userHome, "login", baseUrl(), "token-terminal");
        ProcessResult sessions = runCli(userHome, "sessions");
        ProcessResult resume = runCli(userHome, "resume", "101");
        CliConfig savedConfig = new CliConfigStore(userHome).load();

        assertEquals(0, login.exitCode(), login.output());
        assertEquals(0, sessions.exitCode(), sessions.output());
        assertEquals(0, resume.exitCode(), resume.output());
        assertEquals("/api/chat/conversations", captured.path);
        assertEquals("20", captured.query.get("pageSize"));
        assertEquals("token-terminal", captured.headers.get("satoken"));
        assertTrue(sessions.output().contains("最近会话"), sessions.output());
        assertTrue(sessions.output().contains("101"), sessions.output());
        assertTrue(sessions.output().contains("终端验收会话"), sessions.output());
        assertTrue(resume.output().contains("101"), resume.output());
        assertEquals("101", savedConfig.lastSessionId());
        assertEquals("token-terminal", savedConfig.token());
    }

    /**
     * 终端 sessions 空列表应给出可读空态，而不是启动 TUI 或输出空白。
     */
    @Test
    void terminalSessionsShouldShowEmptyState() throws Exception {
        CapturedConversationRequest captured = new CapturedConversationRequest();
        backendServer = startConversationServer(captured, 200, """
            {"success":true,"code":"OK","message":"success","data":{"items":[],"hasMore":false,"nextCursor":null}}
            """);
        Path userHome = tempDir.resolve("home");

        runCli(userHome, "login", baseUrl(), "token-terminal");
        ProcessResult sessions = runCli(userHome, "sessions");

        assertEquals(0, sessions.exitCode(), sessions.output());
        assertEquals("token-terminal", captured.headers.get("satoken"));
        assertTrue(sessions.output().contains("暂无可恢复会话"), sessions.output());
    }

    /**
     * Web 后端拒绝 token 时，终端必须沿用后端中文 message，方便用户直接处理登录状态。
     */
    @Test
    void terminalSessionsShouldSurfaceBackendMessageWhenWebRejectsToken() throws Exception {
        CapturedConversationRequest captured = new CapturedConversationRequest();
        backendServer = startConversationServer(captured, 401, """
            {"success":false,"code":"AUTH_EXPIRED","message":"登录已过期，请重新登录","data":null}
            """);
        Path userHome = tempDir.resolve("home");

        runCli(userHome, "login", baseUrl(), "expired-token");
        ProcessResult sessions = runCli(userHome, "sessions");

        assertEquals(1, sessions.exitCode(), sessions.output());
        assertEquals("expired-token", captured.headers.get("satoken"));
        assertTrue(sessions.output().contains("登录已过期，请重新登录"), sessions.output());
    }

    /**
     * resume 非数字会话 ID 应在终端入口被拒绝，并保留旧 lastSessionId 不被污染。
     */
    @Test
    void terminalResumeShouldRejectNonNumericConversationIdWithoutChangingConfig() throws Exception {
        Path userHome = tempDir.resolve("home");
        CliConfigStore configStore = new CliConfigStore(userHome);
        configStore.save(new CliConfig("http://127.0.0.1:5001", "token-terminal", "conservative", "88"));

        ProcessResult resume = runCli(userHome, "resume", "abc");

        assertEquals(1, resume.exitCode(), resume.output());
        assertTrue(resume.output().contains("会话 ID 必须是数字"), resume.output());
        assertEquals("88", configStore.load().lastSessionId());
    }

    /**
     * 以真实 Java 子进程运行 CLI main，确保验证覆盖终端启动、参数解析和 System.exit。
     */
    private ProcessResult runCli(Path userHome, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-Duser.home=" + userHome);
        command.add("-Dfile.encoding=UTF-8");
        command.add("-Dsun.stdout.encoding=UTF-8");
        command.add("-Dsun.stderr.encoding=UTF-8");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(CodingXCli.class.getName());
        command.addAll(List.of(args));

        Process process = new ProcessBuilder(command)
            .directory(Path.of(System.getProperty("user.dir")).toFile())
            .redirectErrorStream(true)
            .start();
        boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        byte[] outputBytes = process.getInputStream().readAllBytes();
        assertTrue(finished, "CLI 子进程超时，输出：" + new String(outputBytes, StandardCharsets.UTF_8));
        return new ProcessResult(process.exitValue(), new String(outputBytes, StandardCharsets.UTF_8));
    }

    /**
     * @return 当前 JDK 的 java 可执行文件路径。
     */
    private String javaExecutable() {
        String executableName = System.getProperty("os.name").toLowerCase().contains("win")
            ? "java.exe"
            : "java";
        return Path.of(System.getProperty("java.home"), "bin", executableName).toString();
    }

    /**
     * 启动 Web 会话列表模拟接口。
     */
    private HttpServer startConversationServer(
        CapturedConversationRequest captured,
        int statusCode,
        String body
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat/conversations", exchange ->
            handleConversationRequest(exchange, captured, statusCode, body));
        server.start();
        return server;
    }

    /**
     * 记录终端进程发出的请求，并返回指定 Web ApiResponse。
     */
    private void handleConversationRequest(
        HttpExchange exchange,
        CapturedConversationRequest captured,
        int statusCode,
        String body
    ) throws IOException {
        URI uri = exchange.getRequestURI();
        captured.path = uri.getPath();
        captured.query = parseQuery(uri.getRawQuery());
        captured.headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((key, values) -> {
            if (!values.isEmpty()) {
                captured.headers.put(key.toLowerCase(), values.getFirst());
            }
        });
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /**
     * 解析查询参数，便于断言 CLI 使用 Web 端分页协议。
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
     * @return 模拟后端根地址。
     */
    private String baseUrl() {
        return "http://127.0.0.1:" + backendServer.getAddress().getPort();
    }

    /**
     * CLI 子进程结果。
     *
     * @param exitCode 子进程退出码。
     * @param output 合并后的 stdout/stderr 输出。
     */
    private record ProcessResult(int exitCode, String output) {
    }

    /**
     * Web 会话请求快照。
     */
    private static class CapturedConversationRequest {
        /** 请求路径。 */
        private String path;
        /** 查询参数。 */
        private Map<String, String> query = new LinkedHashMap<>();
        /** 小写 header 到首个 header 值。 */
        private Map<String, String> headers = new LinkedHashMap<>();
    }
}
