package com.codingx.governance.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.codingx.governance.application.service.RepositoryInstructionContextService;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.persistence.mapper.WorkspaceMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * 验证仓库规范文件上下文服务只读加载市面常见 Agent 规则文件，并排除用户明确不需要的来源。
 */
@ExtendWith(MockitoExtension.class)
class RepositoryInstructionContextServiceTest {

    /** 工作空间 Mapper，用于把 workspaceId 解析为本地工作目录。 */
    @Mock
    private WorkspaceMapper workspaceMapper;

    /**
     * 支持的规则文件应进入上下文，Copilot、superpowers memory 和 .codingx 文件必须被排除。
     * @param tempDir 临时仓库目录。
     * @throws Exception 文件创建失败时抛出。
     */
    @Test
    void buildInstructionContextShouldLoadSupportedInstructionFilesAndExcludeIgnoredPaths(@TempDir Path tempDir)
        throws Exception {
        write(tempDir.resolve("AGENTS.md"), "AGENTS root rule");
        write(tempDir.resolve("CLAUDE.md"), "Claude project rule");
        write(tempDir.resolve("QWEN.md"), "Qwen project rule");
        write(tempDir.resolve("CONVENTIONS.md"), "Aider convention rule");
        write(tempDir.resolve(".cursor/rules/team.mdc"), "Cursor team rule");
        write(tempDir.resolve(".windsurf/rules/style.md"), "Windsurf style rule");
        write(tempDir.resolve(".continue/rules/java.md"), "Continue java rule");
        write(tempDir.resolve(".openhands/microagents/review.md"), "OpenHands review rule");
        write(tempDir.resolve(".kiro/steering/product.md"), "Kiro product steering rule");
        write(tempDir.resolve(".roo/rules-code/backend.md"), "Roo code rule");
        write(tempDir.resolve(".github/copilot-instructions.md"), "Copilot rule should not load");
        write(tempDir.resolve("docs/superpowers/memory/governance/rule.md"), "Superpowers memory should not load");
        write(tempDir.resolve(".codingx/context.md"), "CodingX context should not load");
        write(tempDir.resolve(".codingx/rules.md"), "CodingX rules should not load");
        when(workspaceMapper.selectOne(any())).thenReturn(workspace(tempDir));
        RepositoryInstructionContextService service = new RepositoryInstructionContextService(workspaceMapper);

        String context = service.buildInstructionContext(2002L, 3001L);

        assertTrue(context.contains("# 仓库规范文件"));
        assertTrue(context.contains("AGENTS.md"));
        assertTrue(context.contains("AGENTS root rule"));
        assertTrue(context.contains("CLAUDE.md"));
        assertTrue(context.contains("Claude project rule"));
        assertTrue(context.contains(".cursor/rules/team.mdc"));
        assertTrue(context.contains("Cursor team rule"));
        assertTrue(context.contains(".windsurf/rules/style.md"));
        assertTrue(context.contains("Windsurf style rule"));
        assertTrue(context.contains("QWEN.md"));
        assertTrue(context.contains("Qwen project rule"));
        assertTrue(context.contains("CONVENTIONS.md"));
        assertTrue(context.contains("Aider convention rule"));
        assertTrue(context.contains(".continue/rules/java.md"));
        assertTrue(context.contains("Continue java rule"));
        assertTrue(context.contains(".openhands/microagents/review.md"));
        assertTrue(context.contains("OpenHands review rule"));
        assertTrue(context.contains(".kiro/steering/product.md"));
        assertTrue(context.contains("Kiro product steering rule"));
        assertTrue(context.contains(".roo/rules-code/backend.md"));
        assertTrue(context.contains("Roo code rule"));
        assertFalse(context.contains("copilot-instructions.md"));
        assertFalse(context.contains("Copilot rule should not load"));
        assertFalse(context.contains("docs/superpowers/memory"));
        assertFalse(context.contains("Superpowers memory should not load"));
        assertFalse(context.contains(".codingx/context.md"));
        assertFalse(context.contains(".codingx/rules.md"));
    }

    /**
     * 识别流程应打印命中文件与短预览，但不能把完整大段规范正文写入日志。
     * @param tempDir 临时仓库目录。
     * @throws Exception 文件创建失败时抛出。
     */
    @Test
    void buildInstructionContextShouldLogDiscoveryFlowWithShortPreview(@TempDir Path tempDir) throws Exception {
        write(tempDir.resolve("AGENTS.md"), "第一行规则\n第二行规则\n" + "尾部内容".repeat(80));
        when(workspaceMapper.selectOne(any())).thenReturn(workspace(tempDir));
        Logger logger = (Logger) LoggerFactory.getLogger(RepositoryInstructionContextService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            RepositoryInstructionContextService service = new RepositoryInstructionContextService(workspaceMapper);

            service.buildInstructionContext(2002L, 3001L);
        } finally {
            logger.detachAppender(appender);
        }

        String logs = appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(logs.contains("开始识别仓库规范文件"));
        assertTrue(logs.contains("识别到仓库规范文件"));
        assertTrue(logs.contains("AGENTS.md"));
        assertTrue(logs.contains("第一行规则 第二行规则"));
        assertFalse(logs.contains("尾部内容尾部内容尾部内容尾部内容尾部内容尾部内容尾部内容尾部内容尾部内容尾部内容"));
    }

    /**
     * 超长规则文件应被裁剪，并在日志中提示截断，避免一次模型输入被规则文件撑爆。
     * @param tempDir 临时仓库目录。
     * @throws Exception 文件创建失败时抛出。
     */
    @Test
    void buildInstructionContextShouldTruncateOversizedInstructionFile(@TempDir Path tempDir) throws Exception {
        write(tempDir.resolve("AGENTS.md"), "A".repeat(20_000));
        when(workspaceMapper.selectOne(any())).thenReturn(workspace(tempDir));
        Logger logger = (Logger) LoggerFactory.getLogger(RepositoryInstructionContextService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        String context;
        try {
            RepositoryInstructionContextService service = new RepositoryInstructionContextService(workspaceMapper);

            context = service.buildInstructionContext(2002L, 3001L);
        } finally {
            logger.detachAppender(appender);
        }

        assertTrue(context.length() < 13_000);
        assertTrue(context.contains("内容已截断"));
        assertTrue(appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .anyMatch(message -> message.contains("仓库规范文件内容已截断")));
    }

    /**
     * 候选规范文件如果是指向仓库外的符号链接，必须跳过，避免把本地敏感文件注入模型或写入日志预览。
     * @param tempDir 临时仓库目录。
     * @throws Exception 文件创建失败时抛出。
     */
    @Test
    void buildInstructionContextShouldSkipInstructionSymlinkEscapingWorkspace(@TempDir Path tempDir) throws Exception {
        Path externalFile = Files.createTempFile("codingx-external-instruction", ".md");
        Files.writeString(externalFile, "external secret should never load", StandardCharsets.UTF_8);
        Path instructionLink = tempDir.resolve("AGENTS.md");
        assumeTrue(createSymbolicLinkIfSupported(instructionLink, externalFile), "当前环境不支持创建符号链接");
        when(workspaceMapper.selectOne(any())).thenReturn(workspace(tempDir));
        RepositoryInstructionContextService service = new RepositoryInstructionContextService(workspaceMapper);

        String context = service.buildInstructionContext(2002L, 3001L);

        assertFalse(context.contains("external secret should never load"));
        assertFalse(context.contains("AGENTS.md"));
    }

    /**
     * 使用静态桩模拟符号链接，确保测试不依赖 Windows 开发机的 symlink 权限。
     * @param tempDir 临时仓库目录。
     * @throws Exception 静态桩配置失败时抛出。
     */
    @Test
    void buildInstructionContextShouldNotReadSymbolicInstructionFile(@TempDir Path tempDir) throws Exception {
        Path instructionPath = tempDir.resolve("AGENTS.md").toAbsolutePath().normalize();
        when(workspaceMapper.selectOne(any())).thenReturn(workspace(tempDir));
        try (MockedStatic<Files> files = Mockito.mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.isSymbolicLink(instructionPath)).thenReturn(true);
            files.when(() -> Files.isRegularFile(instructionPath)).thenReturn(true);
            files.when(() -> Files.size(instructionPath)).thenReturn(33L);
            files.when(() -> Files.readString(instructionPath, StandardCharsets.UTF_8))
                .thenReturn("external secret should never load");
            RepositoryInstructionContextService service = new RepositoryInstructionContextService(workspaceMapper);

            String context = service.buildInstructionContext(2002L, 3001L);

            assertFalse(context.contains("external secret should never load"));
            assertFalse(context.contains("AGENTS.md"));
        }
    }

    /**
     * 发现阶段读取文件大小失败时，日志必须带真实 workspaceId，便于排查具体仓库问题。
     * @param tempDir 临时仓库目录。
     * @throws Exception 文件创建或静态桩配置失败时抛出。
     */
    @Test
    void buildInstructionContextShouldLogWorkspaceIdWhenSizeCheckFails(@TempDir Path tempDir) throws Exception {
        Path instructionPath = tempDir.resolve("AGENTS.md").toAbsolutePath().normalize();
        write(instructionPath, "AGENTS root rule");
        when(workspaceMapper.selectOne(any())).thenReturn(workspace(tempDir));
        Logger logger = (Logger) LoggerFactory.getLogger(RepositoryInstructionContextService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (MockedStatic<Files> files = Mockito.mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.size(instructionPath)).thenThrow(new IOException("size unavailable"));
            RepositoryInstructionContextService service = new RepositoryInstructionContextService(workspaceMapper);

            service.buildInstructionContext(2002L, 3001L);
        } finally {
            logger.detachAppender(appender);
        }

        String logs = appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(logs.contains("读取仓库规范文件大小失败"));
        assertTrue(logs.contains("workspaceId=3001"));
        assertFalse(logs.contains("workspaceId=null"));
    }

    private void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private boolean createSymbolicLinkIfSupported(Path link, Path target) throws Exception {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            return false;
        }
    }

    private WorkspaceDO workspace(Path workingDirectory) {
        WorkspaceDO workspace = new WorkspaceDO();
        workspace.setId(3001L);
        workspace.setCreatedBy(2002L);
        workspace.setWorkingDirectory(workingDirectory.toString());
        workspace.setDeleted(0);
        return workspace;
    }
}
