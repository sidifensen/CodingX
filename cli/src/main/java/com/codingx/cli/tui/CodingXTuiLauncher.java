package com.codingx.cli.tui;

import com.codingx.cli.agent.AgentEventSource;
import com.codingx.cli.render.TerminalRenderer;
import com.williamcallahan.tui4j.compat.bubbletea.Program;

import java.nio.file.Path;

/**
 * CodingX TUI 真实启动器，负责用 tui4j Program 接管当前终端。
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
     * @param workspace 当前 CLI 工作区。
     * @param eventSource Agent 事件来源。
     * @param renderer 终端事件渲染器。
     */
    public CodingXTuiLauncher(Path workspace, AgentEventSource eventSource, TerminalRenderer renderer) {
        this.workspace = workspace;
        this.eventSource = eventSource;
        this.renderer = renderer;
    }

    @Override
    public void launch() {
        CodingXTuiModel model = new CodingXTuiModel(workspace, eventSource, renderer);
        new Program(model).withAltScreen().run();
    }
}
