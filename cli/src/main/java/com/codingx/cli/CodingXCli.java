package com.codingx.cli;

import com.codingx.cli.backend.BackendChatEventSource;
import com.codingx.cli.auth.CliAuthService;
import com.codingx.cli.auth.SystemBrowserLauncher;
import com.codingx.cli.command.CliCommandRunner;
import com.codingx.cli.config.CliConfigStore;
import com.codingx.cli.render.TerminalRenderer;
import com.codingx.cli.slash.BackendSlashCommandCatalog;
import com.codingx.cli.slash.SlashCommandCatalog;
import com.codingx.cli.tui.CodingXTuiLauncher;

import java.nio.file.Path;

/**
 * CodingX 独立终端入口，负责组装用户配置、后端聊天流事件源和 TUI 命令分发。
 */
public final class CodingXCli {

    private CodingXCli() {
    }

    /**
     * TUI 内部浏览器登录不直接写 stdout，避免授权 URL 穿透到 TUI 画面破坏普通屏幕渲染。
     */
    static final java.util.function.Consumer<String> TUI_AUTH_OUTPUT_CONSUMER = ignored -> {
    };

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
        CliAuthService commandAuthService = new CliAuthService(
            configStore,
            new SystemBrowserLauncher(),
            System.out::print
        );
        CliAuthService tuiAuthService = new CliAuthService(
            configStore,
            new SystemBrowserLauncher(),
            TUI_AUTH_OUTPUT_CONSUMER
        );
        SlashCommandCatalog slashCommandCatalog = new BackendSlashCommandCatalog(configStore);
        CliCommandRunner runner = new CliCommandRunner(
            configStore,
            new CodingXTuiLauncher(
                Path.of(System.getProperty("user.dir")),
                new BackendChatEventSource(configStore, slashCommandCatalog),
                new TerminalRenderer(),
                tuiAuthService,
                slashCommandCatalog
            ),
            commandAuthService
        );
        CliCommandRunner.Result result = runner.run(args);
        System.out.print(result.output());
        if (result.exitCode() != 0) {
            System.exit(result.exitCode());
        }
    }
}
