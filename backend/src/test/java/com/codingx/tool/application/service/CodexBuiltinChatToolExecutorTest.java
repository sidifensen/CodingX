package com.codingx.tool.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import cn.hutool.json.JSONUtil;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.common.exception.BusinessException;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.tool.domain.model.ChatTool;
import com.codingx.tool.domain.repository.ChatToolRepository;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证 Codex 内置工具执行器的关键行为。
 */
@ExtendWith(MockitoExtension.class)
class CodexBuiltinChatToolExecutorTest {

    @Mock
    private ChatToolRepository chatToolRepository;

    @Mock
    private ChatMcpRepository chatMcpRepository;

    @Mock
    private ChatSkillRepository chatSkillRepository;

    @InjectMocks
    private CodexBuiltinChatToolExecutor codexBuiltinChatToolExecutor;

    /**
     * apply_patch 应在可应用时真实写入文件并返回 diff 结果。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void applyPatchShouldWriteFileAndReturnDiff(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("workspace");
        Files.createDirectories(projectRoot);
        Path targetFile = projectRoot.resolve("README.md");
        Files.writeString(targetFile, "line-1\nline-2\n", StandardCharsets.UTF_8);

        String patch = """
            {
              "patch":"*** Begin Patch\\n*** Update File: README.md\\n@@\\n-line-2\\n+line-2-updated\\n*** End Patch"
            }
            """;
        try {
            ProcessBuilder initBuilder = new ProcessBuilder("git", "init")
                .directory(projectRoot.toFile());
            initBuilder.redirectErrorStream(true);
            Process initProcess = initBuilder.start();
            initProcess.waitFor(5, TimeUnit.SECONDS);
            new ProcessBuilder("git", "config", "user.email", "test@example.com")
                .directory(projectRoot.toFile())
                .start()
                .waitFor(5, TimeUnit.SECONDS);
            new ProcessBuilder("git", "config", "user.name", "tester")
                .directory(projectRoot.toFile())
                .start()
                .waitFor(5, TimeUnit.SECONDS);
            new ProcessBuilder("git", "add", "README.md").directory(projectRoot.toFile()).start().waitFor(5, TimeUnit.SECONDS);
            new ProcessBuilder("git", "commit", "-m", "init").directory(projectRoot.toFile()).start().waitFor(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // 无法初始化 git 仓库时，后续断言会直接暴露真实失败原因。
        }

        ChatToolExecutionContext.bindToolWorkingDirectory(projectRoot);
        try {
            ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute("apply_patch", patch);
            String content = Files.readString(targetFile, StandardCharsets.UTF_8);

            assertEquals("apply_patch", result.toolCode());
            assertTrue(content.contains("line-2-updated"));
            assertTrue(result.content().contains("Patch 已应用"));
            assertNotNull(result.metadata());
            assertEquals(Boolean.TRUE, result.metadata().get("applied"));
            assertTrue(String.valueOf(result.metadata().get("diffPreview")).contains("line-2-updated"));
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * 标准 diff 中如果出现当前工作目录内的绝对路径，工具应先规范化为相对路径再交给 git apply。
     * 业务背景：模型容易把上一轮命令输出的绝对路径直接写进补丁头，不能因此阻断文件创建。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void applyPatchShouldNormalizeWorkspaceAbsolutePathInGitDiff(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("workspace");
        Files.createDirectories(projectRoot);
        Path targetFile = projectRoot.resolve("diary").resolve("index.html");
        String absolutePatchPath = targetFile.toAbsolutePath().normalize().toString().replace('\\', '/');
        String patch = """
            diff --git a/%1$s b/%1$s
            new file mode 100644
            index 0000000..e69de29
            --- /dev/null
            +++ b/%1$s
            @@ -0,0 +1,3 @@
            +<!doctype html>
            +<title>Diary</title>
            +<main>today</main>
            """.formatted(absolutePatchPath).stripTrailing();

        ChatToolExecutionContext.bindToolWorkingDirectory(projectRoot);
        try {
            ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(
                "apply_patch",
                JSONUtil.toJsonStr(Map.of("patch", patch))
            );

            assertEquals("apply_patch", result.toolCode());
            assertTrue(Files.exists(targetFile));
            assertTrue(Files.readString(targetFile, StandardCharsets.UTF_8).contains("<title>Diary</title>"));
            assertEquals(projectRoot.toAbsolutePath().normalize().toString(), result.metadata().get("workingDirectory"));
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * 标准 diff 的 hunk 行数若由模型估算错误，应按实际 +/- 行重算，避免 git apply 在文件尾报 corrupt patch。
     * 业务背景：生成大段 HTML/CSS 时，模型经常把 @@ -0,0 +1,N @@ 的 N 写成示意值而非真实行数。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void applyPatchShouldRecountGitDiffHunkLineCount(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("workspace");
        Files.createDirectories(projectRoot);
        Path targetFile = projectRoot.resolve("calendar").resolve("index.html");
        String patch = """
            diff --git a/calendar/index.html b/calendar/index.html
            new file mode 100644
            index 0000000..e69de29
            --- /dev/null
            +++ b/calendar/index.html
            @@ -0,0 +1,58 @@
            +<!DOCTYPE html>
            +<html lang="zh-CN">
            +<head>
            +  <meta charset="UTF-8">
            +  <title>简单日历</title>
            +</head>
            +<body>
            +  <h1>简单日历</h1>
            +</body>
            +</html>
            """.stripTrailing();

        ChatToolExecutionContext.bindToolWorkingDirectory(projectRoot);
        try {
            ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(
                "apply_patch",
                JSONUtil.toJsonStr(Map.of("patch", patch))
            );

            assertEquals("apply_patch", result.toolCode());
            assertTrue(Files.exists(targetFile));
            assertTrue(Files.readString(targetFile, StandardCharsets.UTF_8).contains("简单日历"));
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * 模型有时会输出标准 diff 头，但在新增文件 hunk 中漏掉每行开头的 +。
     * 这种内容仍然只能写入新文件，执行器应补齐新增标记后再交给 git apply。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void applyPatchShouldRepairBareLinesInNewFileGitDiff(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("workspace");
        Files.createDirectories(projectRoot);
        Path targetFile = projectRoot.resolve("diary").resolve("index.html");
        String patch = """
            diff --git a/diary/index.html b/diary/index.html
            new file mode 100644
            index 0000000..e69de29
            --- /dev/null
            +++ b/diary/index.html
            @@ -0,0 +1,9 @@
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8">
              <title>日记</title>
            </head>
            <body>
              <h1>简单日记</h1>
            </body>
            """.stripTrailing();

        ChatToolExecutionContext.bindToolWorkingDirectory(projectRoot);
        try {
            ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(
                "apply_patch",
                JSONUtil.toJsonStr(Map.of("patch", patch))
            );

            assertEquals("apply_patch", result.toolCode());
            assertTrue(Files.exists(targetFile));
            assertTrue(Files.readString(targetFile, StandardCharsets.UTF_8).contains("<h1>简单日记</h1>"));
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * 模型把同名输出文件误描述成新增文件时，执行器应覆盖当前工作区内的既有文件。
     * 业务背景：用户反复生成 weather.html 等单文件页面时，目标文件已存在但模型仍常输出 new file diff。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void applyPatchShouldOverwriteExistingFileWhenNewFileDiffTargetsSameName(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("workspace");
        Files.createDirectories(projectRoot.resolve("weather"));
        Path targetFile = projectRoot.resolve("weather").resolve("weather.html");
        Files.writeString(
            targetFile,
            "<!doctype html>\n<title>旧天气页</title>\n<main>old weather</main>\n",
            StandardCharsets.UTF_8
        );
        String patch = """
            diff --git a/weather/weather.html b/weather/weather.html
            new file mode 100644
            index 0000000..e69de29
            --- /dev/null
            +++ b/weather/weather.html
            @@ -0,0 +1,4 @@
            +<!doctype html>
            +<title>新天气页</title>
            +<main>sunny</main>
            +<footer>updated by ai</footer>
            """.stripTrailing();

        ChatToolExecutionContext.bindToolWorkingDirectory(projectRoot);
        try {
            ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(
                "apply_patch",
                JSONUtil.toJsonStr(Map.of("patch", patch))
            );

            assertEquals("apply_patch", result.toolCode());
            String content = Files.readString(targetFile, StandardCharsets.UTF_8);
            assertTrue(content.contains("<title>新天气页</title>"));
            assertTrue(content.contains("<footer>updated by ai</footer>"));
            assertTrue(!content.contains("old weather"));
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * 缺少 diff --git 头的标准新增文件补丁也应覆盖同名既有文件。
     * 业务背景：模型有时只输出 `--- /dev/null`/`+++ b/notes.html` 片段，不能绕过同名覆盖兜底。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void applyPatchShouldOverwriteExistingFileWhenStandaloneNewFileDiffTargetsSameName(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("workspace");
        Files.createDirectories(projectRoot);
        Path targetFile = projectRoot.resolve("notes.html");
        Files.writeString(
            targetFile,
            "<!doctype html>\n<title>旧笔记</title>\n<main>old note</main>\n",
            StandardCharsets.UTF_8
        );
        String patch = """
            --- /dev/null
            +++ b/notes.html
            @@ -0,0 +1,3 @@
            +<!doctype html>
            +<title>简单笔记</title>
            +<main>new note</main>
            """.stripTrailing();

        ChatToolExecutionContext.bindToolWorkingDirectory(projectRoot);
        try {
            ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(
                "apply_patch",
                JSONUtil.toJsonStr(Map.of("patch", patch))
            );

            assertEquals("apply_patch", result.toolCode());
            String content = Files.readString(targetFile, StandardCharsets.UTF_8);
            assertTrue(content.contains("<title>简单笔记</title>"));
            assertTrue(content.contains("<main>new note</main>"));
            assertTrue(!content.contains("old note"));
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * standalone 新增文件 hunk 中出现形似旧文件头的正文时，不应被误拆成新的文件块。
     * 业务背景：Markdown、代码片段或分隔线可能包含 `--- ` 开头的内容行。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void applyPatchShouldKeepStandaloneNewFileContentThatLooksLikeOldFileHeader(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("workspace");
        Files.createDirectories(projectRoot);
        Path targetFile = projectRoot.resolve("notes.md");
        Files.writeString(targetFile, "old note\n", StandardCharsets.UTF_8);
        String patch = """
            --- /dev/null
            +++ b/notes.md
            @@ -0,0 +1,4 @@
            # 笔记
            --- old marker
            正文
            结尾
            """.stripTrailing();

        ChatToolExecutionContext.bindToolWorkingDirectory(projectRoot);
        try {
            ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(
                "apply_patch",
                JSONUtil.toJsonStr(Map.of("patch", patch))
            );

            assertEquals("apply_patch", result.toolCode());
            String content = Files.readString(targetFile, StandardCharsets.UTF_8);
            assertTrue(content.contains("--- old marker"));
            assertTrue(content.contains("正文"));
            assertTrue(!content.contains("old note"));
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * 标准 diff 文件头带时间戳时，同名新增文件仍应走整文件覆盖兜底。
     * 业务背景：部分模型会输出 `+++ b/notes.html\t时间戳`，路径解析不能把时间戳误当成文件名。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void applyPatchShouldOverwriteExistingFileWhenNewFileDiffHeaderHasTimestamp(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("workspace");
        Files.createDirectories(projectRoot);
        Path targetFile = projectRoot.resolve("notes.html");
        Files.writeString(
            targetFile,
            "<!doctype html>\n<title>旧笔记</title>\n<main>old note</main>\n",
            StandardCharsets.UTF_8
        );
        String patch = """
            diff --git a/notes.html b/notes.html
            new file mode 100644
            index 0000000..e69de29
            --- /dev/null
            +++ b/notes.html\t2026-05-29 00:00:00 +0800
            @@ -0,0 +1,3 @@
            +<!doctype html>
            +<title>简单笔记</title>
            +<main>new note</main>
            """.stripTrailing();

        ChatToolExecutionContext.bindToolWorkingDirectory(projectRoot);
        try {
            ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(
                "apply_patch",
                JSONUtil.toJsonStr(Map.of("patch", patch))
            );

            assertEquals("apply_patch", result.toolCode());
            String content = Files.readString(targetFile, StandardCharsets.UTF_8);
            assertTrue(content.contains("<title>简单笔记</title>"));
            assertTrue(content.contains("<main>new note</main>"));
            assertTrue(!content.contains("old note"));
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * shell_command 必须在当前工具上下文绑定的工作目录执行，避免误改后端进程目录。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void shellCommandShouldRunInsideBoundWorkspace(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("workspace");
        Files.createDirectories(projectRoot);
        ChatToolExecutionContext.bindToolWorkingDirectory(projectRoot);
        try {
            ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(
                "shell_command",
                "{\"command\":\"Set-Content -Path tool-created.txt -Value local-tool\",\"timeoutMs\":10000}"
            );

            assertTrue(Files.exists(projectRoot.resolve("tool-created.txt")));
            assertEquals(projectRoot.toAbsolutePath().normalize().toString(), result.metadata().get("workingDirectory"));
            assertEquals(0, result.metadata().get("exitCode"));
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * shell_command 的 timeout 必须约束整个进程生命周期，不能被同步读取输出阻塞绕过。
     *
     * @param tempDir 测试临时目录。
     */
    @Test
    void shellCommandShouldStopProcessWhenTimeoutIsReached(@TempDir Path tempDir) {
        ChatToolExecutionContext.bindToolWorkingDirectory(tempDir);
        try {
            long startedAt = System.currentTimeMillis();

            ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(
                "shell_command",
                "{\"command\":\"Start-Sleep -Seconds 3\",\"timeoutMs\":200}"
            );

            assertEquals(Boolean.TRUE, result.metadata().get("timedOut"));
            assertTrue(System.currentTimeMillis() - startedAt < 2500L);
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * exec_command 与 write_stdin 组合应能真实驱动本地交互进程，并回收进程输出。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void execCommandAndWriteStdinShouldDriveInteractiveLocalProcess(@TempDir Path tempDir) throws Exception {
        ChatToolExecutionContext.bindToolWorkingDirectory(tempDir);
        try {
            ChatToolExecutionResult execResult = codexBuiltinChatToolExecutor.execute(
                "exec_command",
                "{\"command\":\"$line = [Console]::In.ReadLine(); Set-Content -Path interactive.txt -Value $line\"}"
            );
            String sessionId = String.valueOf(execResult.metadata().get("sessionId"));

            ChatToolExecutionResult stdinResult = codexBuiltinChatToolExecutor.execute(
                "write_stdin",
                "{\"sessionId\":\"" + sessionId + "\",\"text\":\"from-stdin\",\"waitMs\":3000}"
            );

            assertEquals("write_stdin", stdinResult.toolCode());
            assertEquals(Boolean.FALSE, stdinResult.metadata().get("alive"));
            Path outputFile = tempDir.resolve("interactive.txt");
            for (int attempt = 0; attempt < 50 && !Files.exists(outputFile); attempt++) {
                Thread.sleep(100L);
            }
            assertEquals("from-stdin", Files.readString(outputFile).trim());
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * update_plan 是模型可见的进程内状态工具，应返回结构化步骤供后续轮次理解进度。
     */
    @Test
    void updatePlanShouldPersistStructuredSteps() {
        ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(
            "update_plan",
            "{\"planId\":\"tool-test\",\"steps\":[{\"step\":\"验证工具\",\"status\":\"in_progress\"}]}"
        );

        assertEquals("update_plan", result.toolCode());
        assertTrue(result.content().contains("1 个步骤"));
        assertEquals("tool-test", result.metadata().get("planId"));
        assertTrue(String.valueOf(result.metadata().get("steps")).contains("验证工具"));
    }

    /**
     * view_image 应真实读取本地图片尺寸，而不是只返回数据库配置。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void viewImageShouldReadLocalImageMetadata(@TempDir Path tempDir) throws Exception {
        Path imagePath = tempDir.resolve("sample.png");
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", imagePath.toFile());

        ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(
            "view_image",
            "{\"path\":\"" + imagePath.toString().replace("\\", "\\\\") + "\"}"
        );

        assertEquals("view_image", result.toolCode());
        assertEquals(3, result.metadata().get("width"));
        assertEquals(2, result.metadata().get("height"));
    }

    /**
     * view_image 应兼容远程图片 URL，避免模型把图片地址误传给工具后直接报“路径不存在”。
     *
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void viewImageShouldReadRemoteImageMetadata() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/images/sample.png", exchange -> {
                BufferedImage image = new BufferedImage(4, 5, BufferedImage.TYPE_INT_RGB);
                java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
                ImageIO.write(image, "png", outputStream);
                byte[] bytes = outputStream.toByteArray();
                exchange.getResponseHeaders().add("Content-Type", "image/png");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream responseBody = exchange.getResponseBody()) {
                    responseBody.write(bytes);
                }
            });
            server.start();

            String imageUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/images/sample.png";
            ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(
                "view_image",
                "{\"path\":\"" + imageUrl + "\"}"
            );

            assertEquals("view_image", result.toolCode());
            assertEquals(4, result.metadata().get("width"));
            assertEquals(5, result.metadata().get("height"));
            assertEquals(imageUrl, result.metadata().get("path"));
        } finally {
            server.stop(0);
        }
    }

    /**
     * tool_search 应检索真实工具仓储中的配置，供模型发现可用工具。
     */
    @Test
    void toolSearchShouldReturnRepositoryBackedToolConfigs() {
        when(chatToolRepository.findAll()).thenReturn(List.of(
            ChatTool.builder()
                .toolCode("shell_command")
                .displayName("Shell 命令")
                .description("在本地工作区执行命令")
                .category("codex")
                .build()
        ));

        ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(
            "tool_search",
            "{\"keyword\":\"Shell\"}"
        );

        assertEquals("tool_search", result.toolCode());
        assertTrue(result.content().contains("1 个"));
        @SuppressWarnings("unchecked")
        List<ChatTool> results = (List<ChatTool>) result.metadata().get("results");
        assertEquals("shell_command", results.getFirst().getToolCode());
    }

    /**
     * test_sync_tool 用作工具链连通性探针，应回显模型输入并返回工具总数。
     */
    @Test
    void testSyncToolShouldEchoInputForConnectivityCheck() {
        ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(
            "test_sync_tool",
            "{\"message\":\"ping\"}"
        );

        assertEquals("test_sync_tool", result.toolCode());
        assertTrue(result.content().contains("调用成功"));
        assertEquals("{\"message\":\"ping\"}", result.metadata().get("echo"));
        assertTrue(((Number) result.metadata().get("toolCount")).intValue() > 0);
    }

    /**
     * 未接入真实 Codex session 的多代理工具不能再返回假成功，避免误导模型继续依赖不可用结果。
     */
    @Test
    void unsupportedCodexSessionToolShouldReturnUnavailableError() {
        List<String> unsupportedToolCodes = List.of(
            "list_mcp_resources", "list_mcp_resource_templates", "read_mcp_resource",
            "request_user_input", "spawn_agent", "send_input", "send_message", "wait_agent", "close_agent",
            "resume_agent", "request_plugin_install", "request_permissions", "get_goal", "create_goal",
            "update_goal", "followup_task", "list_agents", "spawn_agents_on_csv", "report_agent_job_result"
        );

        for (String toolCode : unsupportedToolCodes) {
            BusinessException exception = assertThrows(
                BusinessException.class,
                () -> codexBuiltinChatToolExecutor.execute(toolCode, "{\"message\":\"分析代码\"}")
            );

            assertTrue(exception.getMessage().contains("暂未接入真实 Codex 运行时"), toolCode);
        }
    }
}
