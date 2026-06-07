package com.codingx.cli.tui;

import com.codingx.cli.agent.AgentEventSource;
import com.codingx.cli.auth.CliAuthService;
import com.codingx.cli.render.TerminalRenderer;
import com.williamcallahan.tui4j.compat.bubbletea.Program;

import java.nio.file.Path;

/**
 * CodingX TUI 真实启动器，负责用 tui4j Program 在普通终端屏幕中运行。
 */
public class CodingXTuiLauncher implements TuiLauncher {

    /**
     * 当前 CLI 工作区。
     */
    private final Path workspace;

    /**
     * Agent 事件来源。
     */
    private final AgentEventSource eventSource;

    /**
     * 终端事件渲染器。
     */
    private final TerminalRenderer renderer;

    /**
     * CLI 认证服务，用于 TUI 内部 `/login`、`/logout` 命令。
     */
    private final CliAuthService cliAuthService;

    /**
     * @param workspace 当前 CLI 工作区。
     * @param eventSource Agent 事件来源。
     * @param renderer 终端事件渲染器。
     */
    public CodingXTuiLauncher(Path workspace, AgentEventSource eventSource, TerminalRenderer renderer) {
        this(workspace, eventSource, renderer, null);
    }

    /**
     * @param workspace 当前 CLI 工作区。
     * @param eventSource Agent 事件来源。
     * @param renderer 终端事件渲染器。
     * @param cliAuthService CLI 认证服务，可为空以兼容测试和旧构造链路。
     */
    public CodingXTuiLauncher(
        Path workspace,
        AgentEventSource eventSource,
        TerminalRenderer renderer,
        CliAuthService cliAuthService
    ) {
        this.workspace = workspace;
        this.eventSource = eventSource;
        this.renderer = renderer;
        this.cliAuthService = cliAuthService;
    }

    @Override
    public void launch() {
        CodingXTuiModel model = new CodingXTuiModel(workspace, eventSource, renderer, cliAuthService);
        // 品牌卡片先写入普通 shell 输出；后续 tui4j 差量刷新只维护输入区，避免 Windows 终端裁剪掉 logo。
        System.out.print(model.startupBanner() + System.lineSeparator() + System.lineSeparator());
        System.out.flush();
        Program program = new Program(model);
        model.setProgram(program);
        // 保留用户输入 codingx 的 shell 上下文，避免 alt screen 把启动页撑成空白全屏。
        program.run();
    }
}
