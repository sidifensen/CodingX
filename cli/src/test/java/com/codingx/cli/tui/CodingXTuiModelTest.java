package com.codingx.cli.tui;

import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.AgentEventSource;
import com.codingx.cli.agent.AgentEventType;
import com.codingx.cli.agent.MockAgentEventSource;
import com.codingx.cli.render.TerminalRenderer;
import com.williamcallahan.tui4j.compat.bubbletea.KeyPressMessage;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.Key;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyType;
import com.williamcallahan.tui4j.compat.lipgloss.color.NoColor;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    void initialViewShouldShowMewCodeStyleHeaderAndStatusBar() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        String view = model.view();

        assertTrue(view.contains("CodingX v0.1.0"));
        assertTrue(view.contains("GLM-5.1"));
        assertTrue(view.contains(tempDir.resolve("workspace").toString()));
        assertTrue(view.contains("Connected to 1 MCP server(s), 2 tools registered"));
        assertTrue(view.contains("Send a message..."));
        assertTrue(view.contains("Plan on (shift+tab to cycle)"));
        assertTrue(view.contains("Status: ready"));
    }

    @Test
    void submitTaskShouldAppendMewCodeStyleTranscript() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        model.submitTask("分析这个项目");

        String view = model.view();
        assertTrue(view.contains("> 分析这个项目"));
        assertTrue(view.contains("我会先查看当前仓库结构"));
        assertTrue(view.contains("ToolSearch"));
        assertTrue(view.contains("(0.0s)"));
        assertTrue(view.contains("Synthesizing..."));
        assertTrue(view.contains("Task completed: COMPLETED"));
        assertTrue(view.contains("Status: completed"));
        assertFalse(view.contains("[tool] 工具: ls"));
    }

    @Test
    void blankTaskShouldNotChangeTranscriptOrStatus() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        model.submitTask("   ");

        String view = model.view();
        assertFalse(view.contains("ToolSearch"));
        assertFalse(view.contains("Task completed"));
        assertTrue(view.contains("Status: ready"));
    }

    @Test
    void shiftTabShouldTogglePlanMode() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        assertTrue(model.view().contains("Plan on"));

        model.update(new KeyPressMessage(new Key(KeyType.KeyShiftTab)));

        assertTrue(model.view().contains("Plan off"));

        model.update(new KeyPressMessage(new Key(KeyType.KeyShiftTab)));

        assertTrue(model.view().contains("Plan on"));
    }

    @Test
    void errorEventsShouldSetErrorStatus() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new ErrorEventSource(),
            new TerminalRenderer()
        );

        model.submitTask("触发错误");

        String view = model.view();
        assertTrue(view.contains("! Error"));
        assertTrue(view.contains("Status: error"));
    }

    /**
     * 测试专用事件源，用于验证错误事件会进入 transcript 并更新状态栏。
     */
    private static class ErrorEventSource implements AgentEventSource {

        @Override
        public List<AgentEvent> startTurn(String task, Path workspace) {
            return List.of(AgentEvent.of("session", "turn", 1, AgentEventType.ERROR, Map.of(
                "message", "模拟错误"
            )));
        }
    }
}
