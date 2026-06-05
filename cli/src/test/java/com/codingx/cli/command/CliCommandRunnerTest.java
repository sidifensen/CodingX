package com.codingx.cli.command;

import com.codingx.cli.config.CliConfigStore;
import com.codingx.cli.tui.TuiLauncher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CLI 命令分发测试，覆盖用户最先会使用的登录和 TUI 入口。
 */
class CliCommandRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void emptyArgsShouldLaunchTui() {
        FakeTuiLauncher tuiLauncher = new FakeTuiLauncher();
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(tempDir.resolve("home")),
            tuiLauncher
        );

        CliCommandRunner.Result result = runner.run(new String[] {});

        assertEquals(0, result.exitCode());
        assertEquals(1, tuiLauncher.launchCount());
        assertTrue(result.output().isEmpty());
    }

    @Test
    void tuiCommandShouldLaunchTui() {
        FakeTuiLauncher tuiLauncher = new FakeTuiLauncher();
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(tempDir.resolve("home")),
            tuiLauncher
        );

        CliCommandRunner.Result result = runner.run(new String[] {"tui"});

        assertEquals(0, result.exitCode());
        assertEquals(1, tuiLauncher.launchCount());
        assertTrue(result.output().isEmpty());
    }

    @Test
    void execShouldBeRejectedBecauseTasksRunInTui() {
        FakeTuiLauncher tuiLauncher = new FakeTuiLauncher();
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(tempDir.resolve("home")),
            tuiLauncher
        );

        CliCommandRunner.Result result = runner.run(new String[] {"exec", "分析这个项目"});

        assertEquals(1, result.exitCode());
        assertEquals(0, tuiLauncher.launchCount());
        assertTrue(result.output().contains("只支持 TUI 交互模式"));
    }

    @Test
    void resumeAndSessionsShouldBeRejectedBecauseSessionOperationsRunInTui() {
        FakeTuiLauncher tuiLauncher = new FakeTuiLauncher();
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(tempDir.resolve("home")),
            tuiLauncher
        );

        CliCommandRunner.Result resumeResult = runner.run(new String[] {"resume"});
        CliCommandRunner.Result sessionsResult = runner.run(new String[] {"sessions"});

        assertEquals(1, resumeResult.exitCode());
        assertEquals(1, sessionsResult.exitCode());
        assertEquals(0, tuiLauncher.launchCount());
        assertTrue(resumeResult.output().contains("只支持 TUI 交互模式"));
        assertTrue(sessionsResult.output().contains("只支持 TUI 交互模式"));
    }

    @Test
    void loginShouldSaveConfigOutsideWorkspace() {
        FakeTuiLauncher tuiLauncher = new FakeTuiLauncher();
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(tempDir.resolve("home")),
            tuiLauncher
        );

        CliCommandRunner.Result result = runner.run(new String[] {"login", "http://localhost:5001", "token-123"});

        assertEquals(0, result.exitCode());
        assertEquals(0, tuiLauncher.launchCount());
        assertTrue(result.output().contains("登录配置已保存"));
        assertTrue(Files.exists(tempDir.resolve("home").resolve(".codingx").resolve("cli.yml")));
    }

    /**
     * 测试专用 TUI 启动器，只记录启动次数，避免单测进入真实全屏终端。
     */
    private static class FakeTuiLauncher implements TuiLauncher {

        /**
         * 命令层调用 TUI 的次数。
         */
        private int launchCount;

        @Override
        public void launch() {
            launchCount++;
        }

        int launchCount() {
            return launchCount;
        }
    }
}
