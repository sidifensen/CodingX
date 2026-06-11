package com.codingx.cli.command;

import com.codingx.cli.config.CliConfigStore;
import com.codingx.cli.auth.CliAuthService;
import com.codingx.cli.config.CliConfig;
import com.codingx.cli.session.CliConversation;
import com.codingx.cli.session.CliConversationService;
import com.codingx.cli.tui.TuiLauncher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
            tuiLauncher,
            new FakeCliAuthService()
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
            tuiLauncher,
            new FakeCliAuthService()
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
            tuiLauncher,
            new FakeCliAuthService()
        );

        CliCommandRunner.Result result = runner.run(new String[] {"exec", "分析这个项目"});

        assertEquals(1, result.exitCode());
        assertEquals(0, tuiLauncher.launchCount());
        assertTrue(result.output().contains("只支持 TUI 交互模式"));
    }

    @Test
    void execShouldRemainRejectedAfterSessionCommandsAreEnabled() {
        FakeTuiLauncher tuiLauncher = new FakeTuiLauncher();
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(tempDir.resolve("home")),
            tuiLauncher,
            new FakeCliAuthService()
        );

        CliCommandRunner.Result execResult = runner.run(new String[] {"exec", "分析这个项目"});

        assertEquals(1, execResult.exitCode());
        assertEquals(0, tuiLauncher.launchCount());
        assertTrue(execResult.output().contains("只支持 TUI 交互模式"));
    }

    @Test
    void sessionsShouldListRecentBackendConversationsWithoutLaunchingTui() {
        FakeTuiLauncher tuiLauncher = new FakeTuiLauncher();
        FakeConversationService conversationService = new FakeConversationService(List.of(
            new CliConversation("101", "修复登录", "ACTIVE", "2026-06-11T10:00:00", "9", "LOCAL"),
            new CliConversation("102", "聊天优化", "ACTIVE", "2026-06-11T09:00:00", "", "CLOUD")
        ));
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(tempDir.resolve("home")),
            tuiLauncher,
            new FakeCliAuthService(),
            conversationService
        );

        CliCommandRunner.Result result = runner.run(new String[] {"sessions"});

        assertEquals(0, result.exitCode());
        assertEquals(0, tuiLauncher.launchCount());
        assertEquals(20, conversationService.requestedPageSizes.getFirst());
        assertTrue(result.output().contains("最近会话"));
        assertTrue(result.output().contains("101"));
        assertTrue(result.output().contains("修复登录"));
        assertTrue(result.output().contains("LOCAL"));
    }

    @Test
    void sessionsShouldShowEmptyStateWithoutLaunchingTui() {
        FakeTuiLauncher tuiLauncher = new FakeTuiLauncher();
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(tempDir.resolve("home")),
            tuiLauncher,
            new FakeCliAuthService(),
            new FakeConversationService(List.of())
        );

        CliCommandRunner.Result result = runner.run(new String[] {"sessions"});

        assertEquals(0, result.exitCode());
        assertEquals(0, tuiLauncher.launchCount());
        assertTrue(result.output().contains("暂无可恢复会话"));
    }

    @Test
    void sessionsShouldReturnBackendErrorMessage() {
        FakeTuiLauncher tuiLauncher = new FakeTuiLauncher();
        FakeConversationService conversationService = new FakeConversationService(List.of());
        conversationService.failure = new IllegalStateException("登录已过期，请重新登录");
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(tempDir.resolve("home")),
            tuiLauncher,
            new FakeCliAuthService(),
            conversationService
        );

        CliCommandRunner.Result result = runner.run(new String[] {"sessions"});

        assertEquals(1, result.exitCode());
        assertEquals(0, tuiLauncher.launchCount());
        assertTrue(result.output().contains("登录已过期，请重新登录"));
    }

    @Test
    void resumeWithConversationIdShouldSaveLastSessionIdWithoutLaunchingTui() {
        FakeTuiLauncher tuiLauncher = new FakeTuiLauncher();
        CliConfigStore configStore = new CliConfigStore(tempDir.resolve("home"));
        configStore.save(new CliConfig("http://localhost:5001", "token-123", "conservative", null));
        CliCommandRunner runner = new CliCommandRunner(
            configStore,
            tuiLauncher,
            new FakeCliAuthService(),
            new FakeConversationService(List.of())
        );

        CliCommandRunner.Result result = runner.run(new String[] {"resume", "101"});

        assertEquals(0, result.exitCode());
        assertEquals(0, tuiLauncher.launchCount());
        assertTrue(result.output().contains("101"));
        assertEquals("101", configStore.load().lastSessionId());
        assertEquals("token-123", configStore.load().token());
    }

    @Test
    void resumeWithoutConversationIdShouldExplainHowToListSessions() {
        FakeTuiLauncher tuiLauncher = new FakeTuiLauncher();
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(tempDir.resolve("home")),
            tuiLauncher,
            new FakeCliAuthService(),
            new FakeConversationService(List.of())
        );

        CliCommandRunner.Result result = runner.run(new String[] {"resume"});

        assertEquals(1, result.exitCode());
        assertEquals(0, tuiLauncher.launchCount());
        assertTrue(result.output().contains("codingx sessions"));
    }

    @Test
    void resumeShouldRejectNonNumericConversationId() {
        FakeTuiLauncher tuiLauncher = new FakeTuiLauncher();
        CliConfigStore configStore = new CliConfigStore(tempDir.resolve("home"));
        configStore.save(new CliConfig("http://localhost:5001", "token-123", "conservative", "88"));
        CliCommandRunner runner = new CliCommandRunner(
            configStore,
            tuiLauncher,
            new FakeCliAuthService(),
            new FakeConversationService(List.of())
        );

        CliCommandRunner.Result result = runner.run(new String[] {"resume", "abc"});

        assertEquals(1, result.exitCode());
        assertEquals(0, tuiLauncher.launchCount());
        assertTrue(result.output().contains("会话 ID 必须是数字"));
        assertEquals("88", configStore.load().lastSessionId());
    }

    @Test
    void loginShouldSaveConfigOutsideWorkspace() {
        FakeTuiLauncher tuiLauncher = new FakeTuiLauncher();
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(tempDir.resolve("home")),
            tuiLauncher,
            new FakeCliAuthService()
        );

        CliCommandRunner.Result result = runner.run(new String[] {"login", "http://localhost:5001", "token-123"});

        assertEquals(0, result.exitCode());
        assertEquals(0, tuiLauncher.launchCount());
        assertTrue(result.output().contains("登录配置已保存"));
        assertTrue(Files.exists(tempDir.resolve("home").resolve(".codingx").resolve("cli.yml")));
    }

    @Test
    void authLoginShouldUseBrowserLoginWithoutLaunchingTui() {
        FakeTuiLauncher tuiLauncher = new FakeTuiLauncher();
        FakeCliAuthService authService = new FakeCliAuthService();
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(tempDir.resolve("home")),
            tuiLauncher,
            authService
        );

        CliCommandRunner.Result result = runner.run(new String[] {"auth", "login"});

        assertEquals(0, result.exitCode());
        assertEquals(0, tuiLauncher.launchCount());
        assertEquals(1, authService.browserLoginCount);
        assertTrue(result.output().contains("CLI 登录成功"));
    }

    @Test
    void authLoginDeviceShouldUseDeviceCodeWithoutLaunchingTui() {
        FakeTuiLauncher tuiLauncher = new FakeTuiLauncher();
        FakeCliAuthService authService = new FakeCliAuthService();
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(tempDir.resolve("home")),
            tuiLauncher,
            authService
        );

        CliCommandRunner.Result result = runner.run(new String[] {"auth", "login", "--device"});

        assertEquals(0, result.exitCode());
        assertEquals(0, tuiLauncher.launchCount());
        assertEquals(1, authService.deviceLoginCount);
        assertTrue(result.output().contains("CLI 登录成功"));
    }

    @Test
    void logoutShouldClearCliLoginWithoutLaunchingTui() {
        FakeTuiLauncher tuiLauncher = new FakeTuiLauncher();
        FakeCliAuthService authService = new FakeCliAuthService();
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(tempDir.resolve("home")),
            tuiLauncher,
            authService
        );

        CliCommandRunner.Result result = runner.run(new String[] {"logout"});

        assertEquals(0, result.exitCode());
        assertEquals(0, tuiLauncher.launchCount());
        assertEquals(1, authService.logoutCount);
        assertTrue(result.output().contains("CLI 已退出登录"));
    }

    @Test
    void authLogoutShouldClearCliLoginWithoutLaunchingTui() {
        FakeTuiLauncher tuiLauncher = new FakeTuiLauncher();
        FakeCliAuthService authService = new FakeCliAuthService();
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(tempDir.resolve("home")),
            tuiLauncher,
            authService
        );

        CliCommandRunner.Result result = runner.run(new String[] {"auth", "logout"});

        assertEquals(0, result.exitCode());
        assertEquals(0, tuiLauncher.launchCount());
        assertEquals(1, authService.logoutCount);
        assertTrue(result.output().contains("CLI 已退出登录"));
    }

    /**
     * 测试专用 TUI 启动器，只记录启动次数，避免单测进入真实交互终端。
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

    /**
     * 测试专用认证服务，只记录调用次数，避免命令测试打开真实浏览器。
     */
    private static class FakeCliAuthService extends CliAuthService {

        /**
         * 浏览器登录调用次数。
         */
        private int browserLoginCount;

        /**
         * 设备码登录调用次数。
         */
        private int deviceLoginCount;

        /**
         * 退出登录调用次数。
         */
        private int logoutCount;

        FakeCliAuthService() {
            super(null, null, ignored -> {
            });
        }

        @Override
        public boolean loginWithBrowser() {
            browserLoginCount++;
            return true;
        }

        @Override
        public boolean loginWithDeviceCode() {
            deviceLoginCount++;
            return true;
        }

        @Override
        public boolean logout() {
            logoutCount++;
            return true;
        }
    }

    /**
     * 测试专用会话服务，避免命令测试访问真实后端。
     */
    private static class FakeConversationService implements CliConversationService {

        /**
         * 后端返回的会话快照。
         */
        private final List<CliConversation> conversations;

        /**
         * 命令层请求过的 pageSize。
         */
        private final List<Integer> requestedPageSizes = new ArrayList<>();

        /**
         * 可选失败，用于验证后端中文错误能透传到 CLI。
         */
        private RuntimeException failure;

        FakeConversationService(List<CliConversation> conversations) {
            this.conversations = conversations;
        }

        @Override
        public List<CliConversation> listRecentConversations(int pageSize) {
            requestedPageSizes.add(pageSize);
            if (failure != null) {
                throw failure;
            }
            return conversations;
        }
    }
}
