package com.codingx.cli.command;

import com.codingx.cli.auth.CliAuthService;
import com.codingx.cli.config.CliConfig;
import com.codingx.cli.config.CliConfigStore;
import com.codingx.cli.tui.TuiLauncher;

/**
 * CLI 命令分发器，负责把用户输入转成配置读写或 TUI 启动动作。
 */
public class CliCommandRunner {

    /**
     * 用户级配置读写器，确保登录令牌不落入当前项目工作区。
     */
    private final CliConfigStore configStore;

    /**
     * TUI 启动器；任务运行只能通过该交互界面触发。
     */
    private final TuiLauncher tuiLauncher;

    /**
     * CLI 登录服务，用于浏览器授权和设备码授权。
     */
    private final CliAuthService cliAuthService;

    /**
     * @param configStore 用户级配置读写器。
     * @param tuiLauncher TUI 启动器。
     * @param cliAuthService CLI 登录服务，负责浏览器授权和设备码授权。
     */
    public CliCommandRunner(
        CliConfigStore configStore,
        TuiLauncher tuiLauncher,
        CliAuthService cliAuthService
    ) {
        this.configStore = configStore;
        this.tuiLauncher = tuiLauncher;
        this.cliAuthService = cliAuthService;
    }

    /**
     * 执行 CLI 命令并返回结果；任务执行入口只允许进入 TUI。
     *
     * @param args 命令行参数。
     * @return 命令结果。
     */
    public Result run(String[] args) {
        if (args.length == 0) {
            return launchTui();
        }
        return switch (args[0]) {
            case "login" -> login(args);
            case "auth" -> auth(args);
            case "tui" -> launchTui();
            case "exec" -> tuiOnly();
            case "resume", "sessions" -> tuiOnly();
            default -> launchTui();
        };
    }

    /**
     * 执行 CLI 认证命令。
     *
     * @param args 命令行参数。
     * @return 认证命令结果。
     */
    private Result auth(String[] args) {
        if (args.length < 2 || !"login".equals(args[1])) {
            return new Result(1, "用法: codingx auth login [--device]" + System.lineSeparator());
        }
        boolean success = args.length >= 3 && "--device".equals(args[2])
            ? cliAuthService.loginWithDeviceCode()
            : cliAuthService.loginWithBrowser();
        return new Result(success ? 0 : 1, success
            ? "CLI 登录成功" + System.lineSeparator()
            : "CLI 登录失败" + System.lineSeparator());
    }

    /**
     * 保存后端地址和 satoken；登录是配置动作，不属于任务运行入口。
     *
     * @param args 命令行参数。
     * @return 登录配置写入结果。
     */
    private Result login(String[] args) {
        if (args.length < 3) {
            return new Result(1, "用法: codingx login <serverUrl> <satoken>" + System.lineSeparator());
        }

        // 登录阶段只保存后端地址和 satoken；会话信息等真实 Agent API 接入后再回填。
        configStore.save(new CliConfig(args[1], args[2], "conservative", null));
        return new Result(0, "登录配置已保存: " + configStore.configFile() + System.lineSeparator());
    }

    /**
     * 启动 TUI 交互界面；默认入口和 `tui` 命令都走这里。
     *
     * @return 空输出的成功结果。
     */
    private Result launchTui() {
        tuiLauncher.launch();
        return new Result(0, "");
    }

    /**
     * 拒绝非交互任务和会话命令，避免 CLI 同时存在两套产品形态。
     *
     * @return TUI-only 中文提示。
     */
    private Result tuiOnly() {
        return new Result(1, "CodingX CLI 只支持 TUI 交互模式，请运行 codingx 或 codingx tui 后在输入框里提交任务。"
            + System.lineSeparator());
    }

    /**
     * CLI 命令执行结果。
     *
     * @param exitCode 进程退出码。
     * @param output 标准输出内容。
     */
    public record Result(int exitCode, String output) {
    }
}
