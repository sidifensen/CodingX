package com.codingx.tool.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import cn.hutool.json.JSONUtil;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.chat.application.service.goal.ChatGoalService;
import com.codingx.chat.application.service.goal.ChatGoalView;
import com.codingx.common.exception.BusinessException;
import com.codingx.skill.domain.model.ChatSkill;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private ChatGoalService chatGoalService;

    @InjectMocks
    private CodexBuiltinChatToolExecutor codexBuiltinChatToolExecutor;

    /**
     * 目标工具缺少会话治理上下文时必须返回中文业务错误，避免脱离聊天流污染全局目标状态。
     */
    @Test
    void goalToolsShouldRequireConversationContext() {
        ChatToolExecutionContext.clear();

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> codexBuiltinChatToolExecutor.execute("create_goal", "{\"title\":\"无上下文目标\"}")
        );

        assertEquals("CHAT_TOOL_GOAL_CONTEXT_REQUIRED", exception.getCode());
        assertTrue(exception.getMessage().contains("目标工具必须在聊天会话中执行"));
    }

    /**
     * create_goal 必须从当前工具上下文读取 userId、conversationId、runId，并委托目标服务写库。
     */
    @Test
    void createGoalShouldUseChatGoalServiceWithGovernanceContext() {
        ChatGoalView goalView = sampleGoalView("9001", "2001", "目标已创建", "GOAL_CREATED");
        when(chatGoalService.createGoal(
            org.mockito.ArgumentMatchers.eq(2001L),
            org.mockito.ArgumentMatchers.eq(1001L),
            org.mockito.ArgumentMatchers.eq(3001L),
            org.mockito.ArgumentMatchers.any(ChatGoalService.CreateGoalCommand.class)
        )).thenReturn(goalView);
        ChatToolExecutionContext.bindGovernanceContext(1001L, 2001L, 3001L);

        try {
            ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(
                "create_goal",
                "{\"goalKey\":\"default\",\"title\":\"真实目标模式\",\"steps\":[{\"key\":\"schema\",\"title\":\"建表\",\"status\":\"pending\"}]}"
            );

            assertEquals("create_goal", result.toolCode());
            assertEquals(goalView, result.metadata().get("goal"));
            ArgumentCaptor<ChatGoalService.CreateGoalCommand> commandCaptor = ArgumentCaptor.forClass(ChatGoalService.CreateGoalCommand.class);
            org.mockito.Mockito.verify(chatGoalService).createGoal(
                org.mockito.ArgumentMatchers.eq(2001L),
                org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.eq(3001L),
                commandCaptor.capture()
            );
            assertEquals("default", commandCaptor.getValue().goalKey());
            assertEquals("真实目标模式", commandCaptor.getValue().title());
            assertEquals("schema", commandCaptor.getValue().steps().getFirst().stepKey());
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * get_goal 必须按当前会话委托目标服务查询，不能再读取执行器内存 Map。
     */
    @Test
    void getGoalShouldReadGoalFromChatGoalService() {
        ChatGoalView goalView = sampleGoalView("9001", "2001", "目标读取成功", "GOAL_UPDATED");
        when(chatGoalService.getGoal(2001L, 1001L, null, "default")).thenReturn(Optional.of(goalView));
        ChatToolExecutionContext.bindGovernanceContext(1001L, 2001L, 3001L);

        try {
            ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute("get_goal", "{\"goalKey\":\"default\"}");

            assertEquals("get_goal", result.toolCode());
            assertEquals(goalView, result.metadata().get("goal"));
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * get_goal 未传目标标识时必须读取当前 active goal，不能隐式改写为 default key。
     */
    @Test
    void getGoalWithoutIdentifierShouldReadActiveGoal() {
        ChatGoalView goalView = sampleGoalView("9002", "2001", "当前活动目标", "GOAL_UPDATED");
        when(chatGoalService.getGoal(2001L, 1001L, null, null)).thenReturn(Optional.of(goalView));
        ChatToolExecutionContext.bindGovernanceContext(1001L, 2001L, 3001L);

        try {
            ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute("get_goal", "{}");

            assertEquals("get_goal", result.toolCode());
            assertEquals(goalView, result.metadata().get("goal"));
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * update_goal 必须把状态和步骤更新委托目标服务，后续由服务追加事件并发布 SSE。
     */
    @Test
    void updateGoalShouldDelegateStatusAndStepsToChatGoalService() {
        ChatGoalView goalView = sampleGoalView("9001", "2001", "目标已完成", "GOAL_COMPLETED");
        when(chatGoalService.updateGoal(
            org.mockito.ArgumentMatchers.eq(2001L),
            org.mockito.ArgumentMatchers.eq(1001L),
            org.mockito.ArgumentMatchers.eq(3001L),
            org.mockito.ArgumentMatchers.any(ChatGoalService.UpdateGoalCommand.class)
        )).thenReturn(goalView);
        ChatToolExecutionContext.bindGovernanceContext(1001L, 2001L, 3001L);

        try {
            ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(
                "update_goal",
                "{\"goalId\":\"9001\",\"status\":\"completed\",\"progressSummary\":\"已完成\",\"steps\":[{\"key\":\"schema\",\"title\":\"建表\",\"status\":\"completed\"}]}"
            );

            assertEquals("update_goal", result.toolCode());
            assertEquals(goalView, result.metadata().get("goal"));
            ArgumentCaptor<ChatGoalService.UpdateGoalCommand> commandCaptor = ArgumentCaptor.forClass(ChatGoalService.UpdateGoalCommand.class);
            org.mockito.Mockito.verify(chatGoalService).updateGoal(
                org.mockito.ArgumentMatchers.eq(2001L),
                org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.eq(3001L),
                commandCaptor.capture()
            );
            assertEquals("9001", commandCaptor.getValue().goalId());
            assertEquals("completed", commandCaptor.getValue().status());
            assertEquals("schema", commandCaptor.getValue().steps().getFirst().stepKey());
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

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
            assertFileDiffMetadata(
                result.metadata(),
                "README.md",
                1,
                1,
                "line-2-updated"
            );
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
     * write 工具需要兼容模型函数参数中的多行 HTML 内容。
     * 业务背景：部分模型会把 HTML 中的真实换行或属性引号直接放入 arguments，严格 JSON 解析失败时不能把整段
     * arguments 当作 path 解析，否则 Windows 会报出包含 <!DOCTYPE html> 的非法路径并中断软件端任务。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void writeShouldRecoverLooseMultilineHtmlArguments(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("workspace");
        Files.createDirectories(projectRoot);
        String looseArguments = """
            {"path":"note.html","content":"<!DOCTYPE html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8">
              <title>笔记</title>
            </head>
            <body>
              <main class="note">今天的计划</main>
            </body>
            </html>"}
            """.stripTrailing();

        ChatToolExecutionContext.bindToolWorkingDirectory(projectRoot);
        try {
            ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute("write", looseArguments);

            Path output = projectRoot.resolve("note.html");
            assertEquals("write", result.toolCode());
            assertTrue(Files.exists(output));
            String content = Files.readString(output, StandardCharsets.UTF_8);
            assertTrue(content.contains("<!DOCTYPE html>"));
            assertTrue(content.contains("<main class=\"note\">今天的计划</main>"));
            assertEquals("note.html", result.metadata().get("path"));
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
     * OpenClaw 风格短工具名应在当前 workspace 内完成常用文件与命令操作。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void shortToolNamesShouldOperateInsideBoundWorkspace(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("workspace");
        Files.createDirectories(projectRoot.resolve("src"));
        Files.writeString(projectRoot.resolve("src").resolve("App.java"), "class App {}\n", StandardCharsets.UTF_8);
        ChatToolExecutionContext.bindToolWorkingDirectory(projectRoot);
        try {
            ChatToolExecutionResult writeResult = codexBuiltinChatToolExecutor.execute(
                "write",
                JSONUtil.toJsonStr(Map.of("path", "notes/todo.txt", "content", "alpha\nbeta\n"))
            );
            ChatToolExecutionResult readResult = codexBuiltinChatToolExecutor.execute(
                "read",
                JSONUtil.toJsonStr(Map.of("path", "notes/todo.txt"))
            );
            ChatToolExecutionResult editResult = codexBuiltinChatToolExecutor.execute(
                "edit",
                JSONUtil.toJsonStr(Map.of("path", "notes/todo.txt", "oldText", "beta", "newText", "done"))
            );
            ChatToolExecutionResult bashResult = codexBuiltinChatToolExecutor.execute(
                "bash",
                JSONUtil.toJsonStr(Map.of("command", "Get-Content -Path notes/todo.txt", "timeoutMs", 10000))
            );
            ChatToolExecutionResult grepResult = codexBuiltinChatToolExecutor.execute(
                "grep",
                JSONUtil.toJsonStr(Map.of("pattern", "class App", "path", "src"))
            );
            ChatToolExecutionResult findResult = codexBuiltinChatToolExecutor.execute(
                "find",
                JSONUtil.toJsonStr(Map.of("pattern", "*.java", "path", "src"))
            );
            ChatToolExecutionResult lsResult = codexBuiltinChatToolExecutor.execute(
                "ls",
                JSONUtil.toJsonStr(Map.of("path", "."))
            );

            assertEquals("write", writeResult.toolCode());
            assertEquals("read", readResult.toolCode());
            assertEquals("edit", editResult.toolCode());
            assertEquals("bash", bashResult.toolCode());
            assertEquals("grep", grepResult.toolCode());
            assertEquals("find", findResult.toolCode());
            assertEquals("ls", lsResult.toolCode());
            assertTrue(readResult.content().contains("alpha"));
            assertTrue(Files.readString(projectRoot.resolve("notes").resolve("todo.txt"), StandardCharsets.UTF_8).contains("done"));
            assertFileDiffMetadata(writeResult.metadata(), "notes/todo.txt", 2, 0, "alpha");
            assertFileDiffMetadata(editResult.metadata(), "notes/todo.txt", 1, 1, "done");
            assertTrue(bashResult.content().contains("done"));
            assertTrue(grepResult.content().contains("src/App.java"));
            assertTrue(findResult.content().contains("src/App.java"));
            assertTrue(lsResult.content().contains("notes"));
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * 右侧代码审查栏需要按来源读取 git 工作区差异，未暂存模式只读当前工作树改动。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void gitDiffShouldReturnUnstagedFileDiffsForReviewSidebar(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("workspace");
        Files.createDirectories(projectRoot);
        Path targetFile = projectRoot.resolve("README.md");
        Files.writeString(targetFile, "old\n", StandardCharsets.UTF_8);
        initGitRepository(projectRoot, "README.md");
        Files.writeString(targetFile, "new\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);

        ChatToolExecutionContext.bindToolWorkingDirectory(projectRoot);
        try {
            ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(
                "git_diff",
                JSONUtil.toJsonStr(Map.of("mode", "unstaged"))
            );

            assertEquals("git_diff", result.toolCode());
            assertFileDiffMetadata(result.metadata(), "README.md", 1, 1, "+new");
            assertEquals("unstaged", result.metadata().get("mode"));
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * 短工具参数应兼容 OpenClaw 的常用调用习惯，降低模型从参考项目迁移时的参数学习成本。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void shortToolNamesShouldAcceptOpenClawCompatibleArguments(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("workspace");
        Files.createDirectories(projectRoot.resolve("src").resolve("nested"));
        Files.writeString(projectRoot.resolve("src").resolve("App.java"), "first\nAlpha[1]\nthird\n", StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("src").resolve("notes.md"), "Alpha[1]\n", StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("src").resolve("nested").resolve("Child.java"), "child\n", StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("duplicate.txt"), "same\nsame\n", StandardCharsets.UTF_8);
        ChatToolExecutionContext.bindToolWorkingDirectory(projectRoot);
        try {
            ChatToolExecutionResult readResult = codexBuiltinChatToolExecutor.execute(
                "read",
                JSONUtil.toJsonStr(Map.of("path", "src/App.java", "offset", 2, "limit", 1))
            );
            ChatToolExecutionResult editResult = codexBuiltinChatToolExecutor.execute(
                "edit",
                JSONUtil.toJsonStr(Map.of("path", "src/App.java", "old_text", "Alpha[1]", "new_text", "Beta[2]"))
            );
            BusinessException duplicateEditException = assertThrows(
                BusinessException.class,
                () -> codexBuiltinChatToolExecutor.execute(
                    "edit",
                    JSONUtil.toJsonStr(Map.of("path", "duplicate.txt", "old_text", "same", "new_text", "done"))
                )
            );
            ChatToolExecutionResult grepResult = codexBuiltinChatToolExecutor.execute(
                "grep",
                JSONUtil.toJsonStr(Map.of(
                    "pattern", "beta[2]",
                    "path", "src",
                    "glob", "*.java",
                    "ignore_case", true,
                    "literal", true,
                    "limit", 1
                ))
            );
            ChatToolExecutionResult findResult = codexBuiltinChatToolExecutor.execute(
                "find",
                JSONUtil.toJsonStr(Map.of("pattern", "*", "path", "src", "limit", 10))
            );
            ChatToolExecutionResult lsResult = codexBuiltinChatToolExecutor.execute(
                "ls",
                JSONUtil.toJsonStr(Map.of("path", "src", "limit", 10))
            );

            assertTrue(readResult.content().startsWith("Alpha[1]"));
            assertTrue(readResult.content().contains("Use offset=3"));
            assertEquals(2, editResult.metadata().get("firstChangedLine"));
            assertTrue(duplicateEditException.getMessage().contains("文本不唯一"));
            assertEquals("same\nsame\n", Files.readString(projectRoot.resolve("duplicate.txt"), StandardCharsets.UTF_8));
            assertTrue(grepResult.content().contains("src/App.java:2:Beta[2]"));
            assertFalse(grepResult.content().contains("notes.md"));
            assertTrue(findResult.content().contains("src/App.java"));
            assertFalse(findResult.content().lines().anyMatch("src/nested"::equals));
            assertTrue(lsResult.content().contains("nested/"));
            assertFalse(lsResult.content().contains("[dir]"));
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * shell_command 必须继承当前聊天绑定的单个 skill 目录，确保技能脚本可通过 CLAUDE_SKILL_DIR 定位。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void shellCommandShouldExposeSingleSkillDirectoryEnvironment(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("workspace");
        Path skillDir = tempDir.resolve("skills").resolve("web-access");
        Files.createDirectories(projectRoot);
        Files.createDirectories(skillDir);
        ChatToolExecutionContext.bindToolWorkingDirectory(projectRoot);
        ChatToolExecutionContext.bindSkillDirectories(Map.of("web-access", skillDir));
        try {
            ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(
                "shell_command",
                JSONUtil.toJsonStr(Map.of("command", echoClaudeSkillDirCommand(), "timeoutMs", 10000))
            );

            assertEquals(0, result.metadata().get("exitCode"));
            assertTrue(result.content().contains(skillDir.toAbsolutePath().normalize().toString()));
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * Windows 服务端实际使用 PowerShell，但很多技能文档沿用 bash 风格的 ${CLAUDE_SKILL_DIR}。
     * 执行器需要兼容该写法，避免 web-access 前置检查被解析到 D:\scripts 一类错误路径。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void shellCommandShouldResolveSkillDirectoryWithBraceVariableSyntax(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("workspace");
        Path skillDir = tempDir.resolve("skills").resolve("web-access");
        Path scriptFile = skillDir.resolve("scripts").resolve("check-deps.mjs");
        Files.createDirectories(projectRoot);
        Files.createDirectories(scriptFile.getParent());
        Files.writeString(scriptFile, "console.log('ok');", StandardCharsets.UTF_8);
        ChatToolExecutionContext.bindToolWorkingDirectory(projectRoot);
        ChatToolExecutionContext.bindSkillDirectories(Map.of("web-access", skillDir));
        try {
            ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(
                "shell_command",
                JSONUtil.toJsonStr(Map.of("command", checkSkillScriptByBraceVariableCommand(), "timeoutMs", 10000))
            );

            assertEquals(0, result.metadata().get("exitCode"));
            assertTrue(result.content().contains("skill-ok"));
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
     * 进程内实现的 Codex 运行时工具必须能被入口真实调用，避免后端提前拦截导致模型无法完成计划、子代理或资源读取链路。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void codexRuntimeToolsShouldBeCallableThroughExecutorEntry(@TempDir Path tempDir) throws Exception {
        when(chatMcpRepository.findAll()).thenReturn(List.of(ChatMcp.builder().mcpCode("weather_query").build()));
        when(chatMcpRepository.findByMcpCode("weather_query")).thenReturn(ChatMcp.builder().mcpCode("weather_query").displayName("天气").build());
        when(chatSkillRepository.findAll()).thenReturn(List.of(ChatSkill.builder().skillCode("browser").build()));
        when(chatToolRepository.findAll()).thenReturn(List.of(ChatTool.builder().toolCode("shell_command").displayName("Shell").build()));
        ChatGoalView goalView = sampleGoalView("tool-check", "2001", "工具检查", "GOAL_UPDATED");
        when(chatGoalService.createGoal(
            org.mockito.ArgumentMatchers.eq(2001L),
            org.mockito.ArgumentMatchers.eq(1001L),
            org.mockito.ArgumentMatchers.eq(3001L),
            org.mockito.ArgumentMatchers.any(ChatGoalService.CreateGoalCommand.class)
        )).thenReturn(goalView);
        when(chatGoalService.getGoal(2001L, 1001L, "tool-check", null)).thenReturn(Optional.of(goalView));
        when(chatGoalService.updateGoal(
            org.mockito.ArgumentMatchers.eq(2001L),
            org.mockito.ArgumentMatchers.eq(1001L),
            org.mockito.ArgumentMatchers.eq(3001L),
            org.mockito.ArgumentMatchers.any(ChatGoalService.UpdateGoalCommand.class)
        )).thenReturn(goalView);

        ChatToolExecutionContext.bindGovernanceContext(1001L, 2001L, 3001L);
        try {
            assertEquals("list_mcp_resources", codexBuiltinChatToolExecutor.execute("list_mcp_resources", "{}").toolCode());
            assertEquals("list_mcp_resource_templates", codexBuiltinChatToolExecutor.execute("list_mcp_resource_templates", "{}").toolCode());
            assertEquals("read_mcp_resource", codexBuiltinChatToolExecutor.execute("read_mcp_resource", "{\"uri\":\"mcp://configs/weather_query\"}").toolCode());
            assertEquals("request_user_input", codexBuiltinChatToolExecutor.execute("request_user_input", "{\"question\":\"是否继续？\"}").toolCode());
            assertEquals("request_plugin_install", codexBuiltinChatToolExecutor.execute("request_plugin_install", "{\"plugin\":\"browser-use\"}").toolCode());
            assertEquals("request_permissions", codexBuiltinChatToolExecutor.execute("request_permissions", "{\"command\":\"Get-ChildItem\"}").toolCode());
            assertEquals("create_goal", codexBuiltinChatToolExecutor.execute("create_goal", "{\"goalId\":\"tool-check\",\"title\":\"工具检查\"}").toolCode());
            assertEquals("get_goal", codexBuiltinChatToolExecutor.execute("get_goal", "{\"goalId\":\"tool-check\"}").toolCode());
            assertEquals("update_goal", codexBuiltinChatToolExecutor.execute("update_goal", "{\"goalId\":\"tool-check\",\"status\":\"done\"}").toolCode());

            ChatToolExecutionResult spawnResult = codexBuiltinChatToolExecutor.execute("spawn_agent", "{\"prompt\":\"检查工具\"}");
            String agentId = String.valueOf(spawnResult.metadata().get("agentId"));
            assertEquals("spawn_agent", spawnResult.toolCode());
            assertEquals("send_input", codexBuiltinChatToolExecutor.execute("send_input", "{\"target\":\"" + agentId + "\",\"message\":\"继续\"}").toolCode());
            assertEquals("wait_agent", codexBuiltinChatToolExecutor.execute("wait_agent", "{\"agentId\":\"" + agentId + "\"}").toolCode());
            assertEquals("followup_task", codexBuiltinChatToolExecutor.execute("followup_task", "{\"agentId\":\"" + agentId + "\",\"task\":\"补充验证\"}").toolCode());
            assertEquals("close_agent", codexBuiltinChatToolExecutor.execute("close_agent", "{\"agentId\":\"" + agentId + "\"}").toolCode());
            assertEquals("resume_agent", codexBuiltinChatToolExecutor.execute("resume_agent", "{\"agentId\":\"" + agentId + "\"}").toolCode());
            assertEquals("list_agents", codexBuiltinChatToolExecutor.execute("list_agents", "{}").toolCode());

            Path csvPath = tempDir.resolve("agents.csv");
            Files.writeString(csvPath, "name\nalpha\n", StandardCharsets.UTF_8);
            assertEquals(
                "spawn_agents_on_csv",
                codexBuiltinChatToolExecutor.execute("spawn_agents_on_csv", "{\"csvPath\":\"" + csvPath.toString().replace("\\", "\\\\") + "\"}").toolCode()
            );
            assertEquals("report_agent_job_result", codexBuiltinChatToolExecutor.execute("report_agent_job_result", "{\"jobId\":\"job-1\",\"summary\":\"ok\"}").toolCode());
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * Electron 本地运行时常见任务会混合命令、补丁、图片、CSV、计划与代理类工具。
     * 此烟测逐个调用执行器注册的所有工具，确保新增工具不会只注册但入口不可用。
     *
     * @param tempDir 测试临时目录。
     * @throws Exception 执行失败时抛出。
     */
    @Test
    void allRegisteredToolsShouldAcceptRepresentativeElectronSmokeInputs(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("workspace");
        Files.createDirectories(projectRoot);
        Path imagePath = tempDir.resolve("smoke.png");
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", imagePath.toFile());
        Path csvPath = tempDir.resolve("agents.csv");
        Files.writeString(csvPath, "name\nsmoke-agent\n", StandardCharsets.UTF_8);
        String htmlPatch = """
            *** Begin Patch
            *** Add File: smoke.html
            +<!doctype html>
            +<html lang="zh-CN">
            +<head><meta charset="UTF-8"><title>工具烟测</title></head>
            +<body><main>electron smoke</main></body>
            +</html>
            *** End Patch
            """;
        List<String> invokedToolCodes = new ArrayList<>();
        when(chatMcpRepository.findAll()).thenReturn(List.of(ChatMcp.builder().mcpCode("weather_query").displayName("天气").build()));
        when(chatMcpRepository.findByMcpCode("weather_query")).thenReturn(ChatMcp.builder().mcpCode("weather_query").displayName("天气").build());
        when(chatSkillRepository.findAll()).thenReturn(List.of(ChatSkill.builder().skillCode("browser").displayName("浏览器").build()));
        when(chatToolRepository.findAll()).thenReturn(List.of(ChatTool.builder().toolCode("shell_command").displayName("Shell").build()));
        ChatGoalView smokeGoal = sampleGoalView("electron-smoke", "2001", "工具烟测", "GOAL_UPDATED");
        when(chatGoalService.createGoal(
            org.mockito.ArgumentMatchers.eq(2001L),
            org.mockito.ArgumentMatchers.eq(1001L),
            org.mockito.ArgumentMatchers.eq(3001L),
            org.mockito.ArgumentMatchers.any(ChatGoalService.CreateGoalCommand.class)
        )).thenReturn(smokeGoal);
        when(chatGoalService.getGoal(2001L, 1001L, "electron-smoke", null)).thenReturn(Optional.of(smokeGoal));
        when(chatGoalService.updateGoal(
            org.mockito.ArgumentMatchers.eq(2001L),
            org.mockito.ArgumentMatchers.eq(1001L),
            org.mockito.ArgumentMatchers.eq(3001L),
            org.mockito.ArgumentMatchers.any(ChatGoalService.UpdateGoalCommand.class)
        )).thenReturn(smokeGoal);

        ChatToolExecutionContext.bindToolWorkingDirectory(projectRoot);
        ChatToolExecutionContext.bindGovernanceContext(1001L, 2001L, 3001L);
        try {
            Files.writeString(projectRoot.resolve("short-tools.txt"), "hello tools\n", StandardCharsets.UTF_8);
            executeAndRecord(invokedToolCodes, "read", JSONUtil.toJsonStr(Map.of("path", "short-tools.txt")));
            executeAndRecord(invokedToolCodes, "write", JSONUtil.toJsonStr(Map.of("path", "short-tools.txt", "content", "hello tools\n")));
            executeAndRecord(
                invokedToolCodes,
                "edit",
                JSONUtil.toJsonStr(Map.of("path", "short-tools.txt", "oldText", "hello", "newText", "updated"))
            );
            executeAndRecord(
                invokedToolCodes,
                "bash",
                JSONUtil.toJsonStr(Map.of("command", "Get-Content -Path short-tools.txt", "timeoutMs", 10000))
            );
            executeAndRecord(invokedToolCodes, "grep", JSONUtil.toJsonStr(Map.of("pattern", "updated", "path", ".")));
            executeAndRecord(invokedToolCodes, "find", JSONUtil.toJsonStr(Map.of("pattern", "*.txt", "path", ".")));
            executeAndRecord(invokedToolCodes, "ls", JSONUtil.toJsonStr(Map.of("path", ".")));
            ChatToolExecutionResult shellResult = executeAndRecord(
                invokedToolCodes,
                "shell_command",
                "{\"command\":\"Set-Content -Path smoke-file.txt -Value electron-smoke\",\"timeoutMs\":10000}"
            );
            executeAndRecord(invokedToolCodes, "apply_patch", JSONUtil.toJsonStr(Map.of("patch", htmlPatch)));
            executeAndRecord(invokedToolCodes, "git_diff", "{\"mode\":\"unstaged\"}");
            executeAndRecord(invokedToolCodes, "list_mcp_resources", "{}");
            executeAndRecord(invokedToolCodes, "list_mcp_resource_templates", "{}");
            executeAndRecord(invokedToolCodes, "read_mcp_resource", "{\"uri\":\"mcp://configs/weather_query\"}");
            executeAndRecord(
                invokedToolCodes,
                "update_plan",
                "{\"planId\":\"electron-smoke\",\"steps\":[{\"step\":\"调用所有工具\",\"status\":\"in_progress\"}]}"
            );
            executeAndRecord(invokedToolCodes, "request_user_input", "{\"question\":\"是否继续工具烟测？\"}");
            executeAndRecord(invokedToolCodes, "view_image", "{\"path\":\"" + imagePath.toString().replace("\\", "\\\\") + "\"}");
            ChatToolExecutionResult spawnResult = executeAndRecord(invokedToolCodes, "spawn_agent", "{\"prompt\":\"检查 Electron 工具\"}");
            String agentId = String.valueOf(spawnResult.metadata().get("agentId"));
            executeAndRecord(invokedToolCodes, "send_input", "{\"target\":\"" + agentId + "\",\"message\":\"继续\"}");
            executeAndRecord(invokedToolCodes, "wait_agent", "{\"agentId\":\"" + agentId + "\"}");
            executeAndRecord(invokedToolCodes, "close_agent", "{\"agentId\":\"" + agentId + "\"}");
            executeAndRecord(invokedToolCodes, "resume_agent", "{\"agentId\":\"" + agentId + "\"}");
            executeAndRecord(invokedToolCodes, "tool_search", "{\"keyword\":\"Shell\"}");
            executeAndRecord(invokedToolCodes, "request_plugin_install", "{\"plugin\":\"browser-use\"}");
            executeAndRecord(invokedToolCodes, "request_permissions", "{\"command\":\"Get-ChildItem\"}");
            ChatToolExecutionResult execResult = executeAndRecord(
                invokedToolCodes,
                "exec_command",
                "{\"command\":\"$line = [Console]::In.ReadLine(); Set-Content -Path stdin-smoke.txt -Value $line\"}"
            );
            String sessionId = String.valueOf(execResult.metadata().get("sessionId"));
            executeAndRecord(
                invokedToolCodes,
                "write_stdin",
                "{\"sessionId\":\"" + sessionId + "\",\"text\":\"from-smoke\",\"waitMs\":3000}"
            );
            codexBuiltinChatToolExecutor.execute("create_goal", "{\"goalId\":\"electron-smoke\",\"title\":\"工具烟测前置目标\"}");
            executeAndRecord(invokedToolCodes, "get_goal", "{\"goalId\":\"electron-smoke\"}");
            executeAndRecord(invokedToolCodes, "create_goal", "{\"goalId\":\"electron-smoke\",\"title\":\"工具烟测\"}");
            executeAndRecord(invokedToolCodes, "update_goal", "{\"goalId\":\"electron-smoke\",\"status\":\"done\"}");
            executeAndRecord(invokedToolCodes, "send_message", "{\"target\":\"" + agentId + "\",\"message\":\"别名消息\"}", "send_input");
            executeAndRecord(invokedToolCodes, "followup_task", "{\"agentId\":\"" + agentId + "\",\"task\":\"补充 HTML 验证\"}");
            executeAndRecord(invokedToolCodes, "list_agents", "{}");
            executeAndRecord(
                invokedToolCodes,
                "spawn_agents_on_csv",
                "{\"csvPath\":\"" + csvPath.toString().replace("\\", "\\\\") + "\"}"
            );
            executeAndRecord(invokedToolCodes, "report_agent_job_result", "{\"jobId\":\"job-smoke\",\"summary\":\"ok\"}");
            executeAndRecord(invokedToolCodes, "test_sync_tool", "{\"message\":\"ping\"}");

            assertEquals(0, shellResult.metadata().get("exitCode"));
            assertTrue(Files.exists(projectRoot.resolve("smoke-file.txt")));
            assertTrue(Files.readString(projectRoot.resolve("stdin-smoke.txt"), StandardCharsets.UTF_8).contains("from-smoke"));
            assertTrue(Files.readString(projectRoot.resolve("smoke.html"), StandardCharsets.UTF_8).contains("electron smoke"));
            assertEquals(codexBuiltinChatToolExecutor.toolCodes(), invokedToolCodes);
        } finally {
            ChatToolExecutionContext.clear();
        }
    }

    /**
     * 按当前测试操作系统生成读取单技能环境变量的命令。
     *
     * @return 可交给 shell_command 执行的命令。
     */
    private static String echoClaudeSkillDirCommand() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            return "if (-not $env:CLAUDE_SKILL_DIR) { exit 2 }; Write-Output $env:CLAUDE_SKILL_DIR";
        }
        return "test -n \"$CLAUDE_SKILL_DIR\" || exit 2; printf '%s' \"$CLAUDE_SKILL_DIR\"";
    }

    /**
     * 按技能文档里的 ${CLAUDE_SKILL_DIR}/scripts 写法生成路径检查命令。
     *
     * @return 可交给 shell_command 执行的命令。
     */
    private static String checkSkillScriptByBraceVariableCommand() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            return "if (-not (Test-Path \"${CLAUDE_SKILL_DIR}/scripts/check-deps.mjs\")) { exit 3 }; Write-Output skill-ok";
        }
        return "test -f \"${CLAUDE_SKILL_DIR}/scripts/check-deps.mjs\" || exit 3; printf 'skill-ok'";
    }

    /**
     * 执行指定工具并记录实际覆盖到的工具编码，便于全量烟测发现注册但未测的工具。
     */
    private ChatToolExecutionResult executeAndRecord(List<String> invokedToolCodes, String toolCode, String question) {
        return executeAndRecord(invokedToolCodes, toolCode, question, toolCode);
    }

    /**
     * 执行指定工具并记录实际覆盖到的工具编码，兼容 send_message 这类入口别名。
     */
    private ChatToolExecutionResult executeAndRecord(
        List<String> invokedToolCodes,
        String toolCode,
        String question,
        String expectedResultToolCode
    ) {
        ChatToolExecutionResult result = codexBuiltinChatToolExecutor.execute(toolCode, question);
        invokedToolCodes.add(toolCode);
        assertEquals(expectedResultToolCode, result.toolCode());
        return result;
    }

    /**
     * 构造目标工具测试用的目标视图，避免烟测关心服务层建模细节。
     */
    private static ChatGoalView sampleGoalView(String goalId, String conversationId, String title, String eventType) {
        return new ChatGoalView(
            goalId,
            conversationId,
            "default",
            title,
            "目标说明",
            "ACTIVE",
            "进行中",
            eventType,
            LocalDateTime.parse("2026-06-09T12:00:00"),
            LocalDateTime.parse("2026-06-09T12:01:00"),
            null,
            List.of(new ChatGoalView.StepView("step-1", "schema", "建表", "PENDING", "等待处理", 0))
        );
    }

    /**
     * 校验文件差异元数据；主消息区和审查侧栏都依赖这些字段做实时渲染。
     */
    private static void assertFileDiffMetadata(
        Map<String, Object> metadata,
        String expectedPath,
        int expectedAdditions,
        int expectedDeletions,
        String expectedDiffFragment
    ) {
        assertNotNull(metadata);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fileDiffs = (List<Map<String, Object>>) metadata.get("fileDiffs");
        assertNotNull(fileDiffs);
        assertFalse(fileDiffs.isEmpty());
        Map<String, Object> matchedDiff = fileDiffs.stream()
            .filter(diff -> expectedPath.equals(diff.get("path")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("缺少文件差异: " + expectedPath + " in " + fileDiffs));
        assertEquals(expectedAdditions, matchedDiff.get("additions"));
        assertEquals(expectedDeletions, matchedDiff.get("deletions"));
        assertTrue(String.valueOf(matchedDiff.get("diff")).contains(expectedDiffFragment));
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) metadata.get("diffSummary");
        assertNotNull(summary);
        assertTrue(Number.class.isAssignableFrom(summary.get("filesChanged").getClass()));
        assertTrue(Number.class.isAssignableFrom(summary.get("additions").getClass()));
        assertTrue(Number.class.isAssignableFrom(summary.get("deletions").getClass()));
        assertTrue(String.valueOf(metadata.get("diffPreview")).contains(expectedDiffFragment));
    }

    /**
     * 初始化一个最小 git 仓库并提交指定文件，供 diff 查询工具生成稳定的工作区差异。
     */
    private static void initGitRepository(Path projectRoot, String filePath) throws Exception {
        runGit(projectRoot, "init");
        runGit(projectRoot, "config", "user.email", "test@example.com");
        runGit(projectRoot, "config", "user.name", "tester");
        runGit(projectRoot, "add", filePath);
        runGit(projectRoot, "commit", "-m", "init");
    }

    /**
     * 执行测试用 git 命令，失败时直接抛出，避免吞掉仓库初始化错误。
     */
    private static void runGit(Path projectRoot, String... args) throws Exception {
        Process process = new ProcessBuilder(concatGitArgs(args))
            .directory(projectRoot.toFile())
            .redirectErrorStream(true)
            .start();
        assertTrue(process.waitFor(5, TimeUnit.SECONDS), "git 命令超时");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
    }

    /**
     * 组装 git 命令参数数组，避免测试里重复拼接。
     */
    private static String[] concatGitArgs(String[] args) {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        return command;
    }
}
