package com.codingx.cli;

import com.codingx.cli.agent.MockAgentEventSource;
import com.codingx.cli.command.CliCommandRunner;
import com.codingx.cli.config.CliConfigStore;
import com.codingx.cli.render.TerminalRenderer;
import com.codingx.cli.tui.CodingXTuiLauncher;

import java.nio.file.Path;

/**
 * CodingX 独立终端入口，第一阶段负责启动本地 CLI 命令分发。
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
        CliCommandRunner runner = new CliCommandRunner(
            new CliConfigStore(Path.of(System.getProperty("user.home"))),
            new CodingXTuiLauncher(
                Path.of(System.getProperty("user.dir")),
                new MockAgentEventSource(),
                new TerminalRenderer()
            )
        );
        CliCommandRunner.Result result = runner.run(args);
        System.out.print(result.output());
        if (result.exitCode() != 0) {
            System.exit(result.exitCode());
        }
    }
}
