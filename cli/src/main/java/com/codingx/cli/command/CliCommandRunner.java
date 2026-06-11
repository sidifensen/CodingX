package com.codingx.cli.command;

import com.codingx.cli.auth.CliAuthService;
import com.codingx.cli.config.CliConfig;
import com.codingx.cli.config.CliConfigStore;
import com.codingx.cli.session.CliConversation;
import com.codingx.cli.session.CliConversationService;
import com.codingx.cli.tui.TuiLauncher;

import java.util.List;
import java.util.regex.Pattern;

/**
 * CLI 命令分发器，负责把用户输入转成配置读写或 TUI 启动动作。
 */
public class CliCommandRunner {

    /**
     * 会话列表默认读取数量，保持终端输出可扫读。
     */
    private static final int DEFAULT_SESSION_PAGE_SIZE = 20;

    /**
     * 后端会话 ID 是 Long，CLI 只允许数值字符串写回 lastSessionId。
     */
    private static final Pattern NUMERIC_CONVERSATION_ID = Pattern.compile("\\d+");

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
     * 后端会话查询服务，用于 `sessions` 和 `resume` 复用 Web 会话历史。
     */
    private final CliConversationService conversationService;

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
        this(configStore, tuiLauncher, cliAuthService, CliConversationService.EMPTY);
    }

    /**
     * @param configStore 用户级配置读写器。
     * @param tuiLauncher TUI 启动器。
     * @param cliAuthService CLI 登录服务，负责浏览器授权和设备码授权。
     * @param conversationService 后端会话查询服务。
     */
    public CliCommandRunner(
        CliConfigStore configStore,
        TuiLauncher tuiLauncher,
        CliAuthService cliAuthService,
        CliConversationService conversationService
    ) {
        this.configStore = configStore;
        this.tuiLauncher = tuiLauncher;
        this.cliAuthService = cliAuthService;
        this.conversationService = conversationService == null ? CliConversationService.EMPTY : conversationService;
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
            case "logout" -> logout();
            case "auth" -> auth(args);
            case "tui" -> launchTui();
            case "exec" -> tuiOnly();
            case "sessions" -> listSessions();
            case "resume" -> resumeSession(args);
            default -> launchTui();
        };
    }

    /**
     * 查询 Web 后端最近会话并输出终端列表，供用户选择 `resume` 目标。
     *
     * @return 命令结果。
     */
    private Result listSessions() {
        List<CliConversation> conversations;
        try {
            conversations = conversationService.listRecentConversations(DEFAULT_SESSION_PAGE_SIZE);
        } catch (RuntimeException exception) {
            return new Result(1, exception.getMessage() + System.lineSeparator());
        }
        if (conversations.isEmpty()) {
            return new Result(0, "暂无可恢复会话。" + System.lineSeparator());
        }
        StringBuilder output = new StringBuilder("最近会话：" + System.lineSeparator());
        for (CliConversation conversation : conversations) {
            output.append("  ")
                .append(conversation.id())
                .append("  ")
                .append(blankToDash(conversation.title()))
                .append("  ")
                .append(blankToDash(conversation.status()))
                .append("  ")
                .append(blankToDash(conversation.updatedAt()))
                .append("  ")
                .append(blankToDash(conversation.workspaceType()))
                .append(System.lineSeparator());
        }
        output.append("使用 codingx resume <会话ID> 恢复目标会话。").append(System.lineSeparator());
        return new Result(0, output.toString());
    }

    /**
     * 将指定会话 ID 写入 CLI 配置，下一次 TUI 提交任务时由聊天流客户端续接。
     *
     * @param args 命令行参数。
     * @return 命令结果。
     */
    private Result resumeSession(String[] args) {
        if (args.length < 2) {
            return new Result(1, "用法: codingx resume <会话ID>。可先运行 codingx sessions 查看可恢复会话。"
                + System.lineSeparator());
        }
        String conversationId = args[1] == null ? "" : args[1].trim();
        if (!NUMERIC_CONVERSATION_ID.matcher(conversationId).matches()) {
            return new Result(1, "会话 ID 必须是数字。" + System.lineSeparator());
        }
        CliConfig currentConfig = configStore.load();
        configStore.save(new CliConfig(
            currentConfig.serverUrl(),
            currentConfig.token(),
            currentConfig.approvalPolicy(),
            conversationId
        ));
        return new Result(0, "已选择会话 " + conversationId + "，下次进入 TUI 将续接该会话。"
            + System.lineSeparator());
    }

    /**
     * 执行 CLI 认证命令。
     *
     * @param args 命令行参数。
     * @return 认证命令结果。
     */
    private Result auth(String[] args) {
        if (args.length < 2) {
            return new Result(1, "用法: codingx auth <login|logout> [--device]" + System.lineSeparator());
        }
        if ("logout".equals(args[1])) {
            return logout();
        }
        if (!"login".equals(args[1])) {
            return new Result(1, "用法: codingx auth <login|logout> [--device]" + System.lineSeparator());
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
     * 清理 CLI 本机登录态；命令层只负责分发，实际 token 清理由认证服务封装。
     *
     * @return 退出登录结果。
     */
    private Result logout() {
        boolean success = cliAuthService.logout();
        return new Result(success ? 0 : 1, success
            ? "CLI 已退出登录" + System.lineSeparator()
            : "CLI 退出登录失败" + System.lineSeparator());
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
     * 拒绝 exec 等非交互任务入口，避免 CLI 同时存在两套任务运行产品形态。
     *
     * @return TUI-only 中文提示。
     */
    private Result tuiOnly() {
        return new Result(1, "CodingX CLI 只支持 TUI 交互模式，请运行 codingx 或 codingx tui 后在输入框里提交任务。"
            + System.lineSeparator());
    }

    /**
     * 终端列表中空字段显示为短横线，避免输出 null。
     */
    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
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
