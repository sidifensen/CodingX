package com.codingx.cli.command;

import com.codingx.cli.agent.MockAgentEventSource;
import com.codingx.cli.config.CliConfigStore;
import com.codingx.cli.render.TerminalRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CLI 命令分发测试，覆盖用户最先会使用的登录和执行入口。
 */
class CliCommandRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void execShouldRenderMockAgentEvents() {
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(tempDir.resolve("home")),
            new MockAgentEventSource(),
            new TerminalRenderer(),
            tempDir.resolve("workspace")
        );

        CliCommandRunner.Result result = runner.run(new String[] {"exec", "分析这个项目"});

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("开始任务: 分析这个项目"));
        assertTrue(result.output().contains("基础终端已可接收 AgentEvent"));
    }

    @Test
    void loginShouldSaveConfigOutsideWorkspace() {
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(tempDir.resolve("home")),
            new MockAgentEventSource(),
            new TerminalRenderer(),
            tempDir.resolve("workspace")
        );

        CliCommandRunner.Result result = runner.run(new String[] {"login", "http://localhost:5001", "token-123"});

        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("登录配置已保存"));
        assertTrue(Files.exists(tempDir.resolve("home").resolve(".codingx").resolve("cli.yml")));
    }
}
