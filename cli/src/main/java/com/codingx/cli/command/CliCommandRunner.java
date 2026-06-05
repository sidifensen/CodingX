package com.codingx.cli.command;

import com.codingx.cli.CodingXCli;
import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.AgentEventSource;
import com.codingx.cli.config.CliConfig;
import com.codingx.cli.config.CliConfigStore;
import com.codingx.cli.render.TerminalRenderer;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * CLI 命令分发器，负责把用户输入转成配置读写或 Agent 事件流渲染。
 */
public class CliCommandRunner {

    /**
     * 用户级配置读写器，确保登录令牌不落入当前项目工作区。
     */
    private final CliConfigStore configStore;

    /**
     * Agent 事件来源；当前注入 mock，后续替换为后端 API 客户端。
     */
    private final AgentEventSource eventSource;

    /**
     * 终端渲染器，负责把统一事件协议转成人类可读输出。
     */
    private final TerminalRenderer renderer;

    /**
     * 当前命令执行目录，后续真实工具调用会以它作为项目工作区。
     */
    private final Path workspace;

    /**
     * @param configStore 用户级配置读写器。
     * @param eventSource Agent 事件来源。
     * @param renderer 终端事件渲染器。
     * @param workspace 当前工作区路径。
     */
    public CliCommandRunner(
        CliConfigStore configStore,
        AgentEventSource eventSource,
        TerminalRenderer renderer,
        Path workspace
    ) {
        this.configStore = configStore;
        this.eventSource = eventSource;
        this.renderer = renderer;
        this.workspace = workspace;
    }

    /**
     * 执行 CLI 命令并返回结果；当前版本只做本地终端 MVP，不直接调用模型。
     *
     * @param args 命令行参数。
     * @return 命令结果。
     */
    public Result run(String[] args) {
        if (args.length == 0) {
            return help();
        }
        return switch (args[0]) {
            case "login" -> login(args);
            case "exec" -> exec(args);
            case "resume" -> new Result(0, "resume: 后续接入后端 AgentSession 后启用" + System.lineSeparator());
            case "sessions" -> new Result(0, "sessions: 后续接入后端 AgentSession 后启用" + System.lineSeparator());
            default -> exec(new String[] {"exec", String.join(" ", args)});
        };
    }

    private Result login(String[] args) {
        if (args.length < 3) {
            return new Result(1, "用法: codingx login <serverUrl> <satoken>" + System.lineSeparator());
        }

        // 登录阶段只保存后端地址和 satoken；会话信息等真实 Agent API 接入后再回填。
        configStore.save(new CliConfig(args[1], args[2], "conservative", null));
        return new Result(0, "登录配置已保存: " + configStore.configFile() + System.lineSeparator());
    }

    private Result exec(String[] args) {
        if (args.length < 2) {
            return new Result(1, "用法: codingx exec <任务>" + System.lineSeparator());
        }
        String task = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (task.isEmpty()) {
            return new Result(1, "用法: codingx exec <任务>" + System.lineSeparator());
        }

        // MVP 中事件源直接返回快照；后续后端流式接入时这里仍只负责编排和输出。
        List<AgentEvent> events = eventSource.startTurn(task, workspace);
        String output = String.join(System.lineSeparator(), renderer.render(events)) + System.lineSeparator();
        return new Result(0, output);
    }

    private Result help() {
        String newline = System.lineSeparator();
        return new Result(0, CodingXCli.productName() + newline
            + "用法:" + newline
            + "  codingx login <serverUrl> <satoken>" + newline
            + "  codingx exec <任务>" + newline
            + "  codingx resume" + newline
            + "  codingx sessions" + newline);
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
