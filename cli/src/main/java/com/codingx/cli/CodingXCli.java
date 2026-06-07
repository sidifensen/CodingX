package com.codingx.cli;

import com.codingx.cli.backend.BackendChatEventSource;
import com.codingx.cli.auth.CliAuthService;
import com.codingx.cli.auth.SystemBrowserLauncher;
import com.codingx.cli.command.CliCommandRunner;
import com.codingx.cli.config.CliConfigStore;
import com.codingx.cli.render.TerminalRenderer;
import com.codingx.cli.tui.CodingXTuiLauncher;

import java.nio.file.Path;

/**
 * CodingX 独立终端入口，负责组装用户配置、后端聊天流事件源和 TUI 命令分发。
 */
public final class CodingXCli {

    private CodingXCli() {
    }

    /**
     * 返回 CLI 产品名，供启动横幅和烟雾测试复用。
     *
     * @return 产品名。
     */
    public static String productName() {
        return "CodingX CLI";
    }

    /**
     * Java CLI 进程入口。
     *
     * @param args 命令行参数。
     */
    public static void main(String[] args) {
        CliConfigStore configStore = new CliConfigStore(Path.of(System.getProperty("user.home")));
        CliAuthService cliAuthService = new CliAuthService(
            configStore,
            new SystemBrowserLauncher(),
            System.out::print
        );
        CliCommandRunner runner = new CliCommandRunner(
            configStore,
            new CodingXTuiLauncher(
                Path.of(System.getProperty("user.dir")),
                new BackendChatEventSource(configStore),
                new TerminalRenderer(),
                cliAuthService
            ),
            cliAuthService
        );
        CliCommandRunner.Result result = runner.run(args);
        System.out.print(result.output());
        if (result.exitCode() != 0) {
            System.exit(result.exitCode());
        }
    }
}
