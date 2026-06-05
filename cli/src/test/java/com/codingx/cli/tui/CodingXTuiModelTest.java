package com.codingx.cli.tui;

import com.codingx.cli.agent.MockAgentEventSource;
import com.codingx.cli.render.TerminalRenderer;
import com.williamcallahan.tui4j.compat.lipgloss.color.NoColor;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TUI 模型测试，直接验证界面状态，不启动真实全屏终端。
 */
class CodingXTuiModelTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUpTerminalInfoProvider() {
        // tui4j 的 Program 会在真实运行时注入 TerminalInfo；单测直接渲染模型时需要提供非 TTY 环境。
        TerminalInfo.provide(() -> new TerminalInfo(false, new NoColor()));
    }

    @Test
    void initialViewShouldShowHeaderWorkspaceAndInputHint() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        String view = model.view();

        assertTrue(view.contains("CodingX TUI"));
        assertTrue(view.contains(tempDir.resolve("workspace").toString()));
        assertTrue(view.contains("输入任务后按 Enter"));
        assertTrue(view.contains("状态: ready"));
    }

    @Test
    void submitTaskShouldAppendUserTaskAndAgentEvents() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        model.submitTask("分析这个项目");

        String view = model.view();
        assertTrue(view.contains("> 分析这个项目"));
        assertTrue(view.contains("开始任务: 分析这个项目"));
        assertTrue(view.contains("[tool] 工具: ls"));
        assertTrue(view.contains("[cmd:stdout]"));
        assertTrue(view.contains("任务完成: COMPLETED"));
        assertTrue(view.contains("状态: completed"));
    }
}
