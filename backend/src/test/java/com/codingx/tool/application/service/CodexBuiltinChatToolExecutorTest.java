package com.codingx.tool.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.tool.domain.repository.ChatToolRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
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
}
