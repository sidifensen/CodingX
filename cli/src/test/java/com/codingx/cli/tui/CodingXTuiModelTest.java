package com.codingx.cli.tui;

import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.AgentEventSource;
import com.codingx.cli.agent.AgentEventType;
import com.codingx.cli.agent.MockAgentEventSource;
import com.codingx.cli.agent.StreamingAgentEventSource;
import com.codingx.cli.render.TerminalRenderer;
import com.williamcallahan.tui4j.compat.bubbletea.KeyPressMessage;
import com.williamcallahan.tui4j.compat.bubbletea.Message;
import com.williamcallahan.tui4j.compat.bubbletea.PrintLineMessage;
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * TUI 模型测试，直接验证界面状态，不启动真实交互终端。
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
    void startupBannerShouldShowCodexStyleShellWithoutFakeModelData() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        String view = model.startupBanner();

        assertTrue(view.contains(">_ CodingX CLI (v0.1.0)"));
        assertTrue(view.contains("model:"));
        assertTrue(view.contains("server selected"));
        assertTrue(view.contains("directory:"));
        assertTrue(view.contains(tempDir.resolve("workspace").toString()));
        assertTrue(view.contains("Tip:"));
        assertTrue(view.contains("Build faster with CodingX."));
        assertFalse(view.contains("Ready. Start with a question"));
        assertFalse(view.contains("Status: ready"));
        assertFalse(view.contains("Describe a task or ask a question..."));
        assertFalse(view.contains("GLM-5.1"));
        assertFalse(view.contains("Connected to 1 MCP server(s), 2 tools registered"));
    }

    @Test
    void initialViewShouldKeepOnlyInteractiveAreaAfterStartupBannerIsPrinted() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        String view = model.view();

        assertTrue(view.contains("› Write tests for @filename"));
        assertTrue(view.contains("server selected ·"));
        assertFalse(view.contains(">_ CodingX CLI (v0.1.0)"));
        assertFalse(view.contains("Tip: Build faster with CodingX."));
        assertTrue(view.lines().count() <= 4);
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
        assertTrue(view.contains("Task completed: COMPLETED"));
        assertTrue(view.contains("completed"));
        assertFalse(view.contains("[tool] 工具: ls"));
    }

    @Test
    void keyboardEnterShouldSubmitVisibleUserInputBeforeResettingComposer() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        pressRunes(model, "hello");
        UpdateResult<?> result = model.update(new KeyPressMessage(new Key(KeyType.keyCR)));

        String view = model.view();
        assertPrintLine(result, "> hello");
        assertFalse(view.contains("> hello"));
        assertTrue(view.contains("我会先查看当前仓库结构"));
        assertTrue(view.contains("› Write tests for @filename"));
    }

    @Test
    void keyboardLineFeedShouldAlsoSubmitVisibleUserInputOnWindowsTerminal() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        pressRunes(model, "hello");
        UpdateResult<?> result = model.update(new KeyPressMessage(new Key(KeyType.keyLF)));

        String view = model.view();
        assertPrintLine(result, "> hello");
        assertFalse(view.contains("> hello"));
        assertTrue(view.contains("我会先查看当前仓库结构"));
        assertTrue(view.contains("› Write tests for @filename"));
    }

    @Test
    void keyboardSubmitShouldPrintUserInputAbovePlainScreenRenderer() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        pressRunes(model, "hello");
        UpdateResult<?> result = model.update(new KeyPressMessage(new Key(KeyType.keyCR)));

        assertPrintLine(result, "> hello");
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
        assertTrue(view.contains("ready"));
    }

    @Test
    void shiftTabShouldTogglePlanMode() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        assertTrue(model.view().contains("Plan mode"));

        model.update(new KeyPressMessage(new Key(KeyType.KeyShiftTab)));

        assertTrue(model.view().contains("Chat mode"));

        model.update(new KeyPressMessage(new Key(KeyType.KeyShiftTab)));

        assertTrue(model.view().contains("Plan mode"));
    }

    @Test
    void submitTaskShouldPassCurrentPlanModeToStreamingEventSource() {
        CapturingStreamingEventSource eventSource = new CapturingStreamingEventSource();
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer()
        );

        model.submitTask("先规划");
        model.update(new KeyPressMessage(new Key(KeyType.KeyShiftTab)));
        model.submitTask("直接执行");

        assertTrue(eventSource.planModes.contains(true));
        assertTrue(eventSource.planModes.contains(false));
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
        assertTrue(view.contains("error"));
    }

    @Test
    void agentEventsMessageShouldAppendStreamedTranscriptAndCompleteStatus() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        model.update(new AgentEventsMessage(List.of(
            AgentEvent.of("67890", "777", 1, AgentEventType.ASSISTANT_DELTA, Map.of(
                "delta", "这是后端流式返回"
            )),
            AgentEvent.of("67890", "777", 2, AgentEventType.TURN_COMPLETED, Map.of(
                "status", "COMPLETED"
            ))
        )));

        String view = model.view();
        assertTrue(view.contains("这是后端流式返回"));
        assertTrue(view.contains("Task completed: COMPLETED"));
        assertTrue(view.contains("completed"));
    }

    @Test
    void streamedAssistantDeltasShouldCoalesceIntoOneConversationBlock() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        model.update(new AgentEventsMessage(List.of(
            AgentEvent.of("67890", "777", 1, AgentEventType.ASSISTANT_DELTA, Map.of(
                "delta", "我会先看项目结构，"
            ))
        )));
        model.update(new AgentEventsMessage(List.of(
            AgentEvent.of("67890", "777", 2, AgentEventType.ASSISTANT_DELTA, Map.of(
                "delta", "再给你改终端界面。"
            ))
        )));

        String view = model.view();
        assertTrue(view.contains("我会先看项目结构，再给你改终端界面。"));
        assertFalse(view.contains("  • 我会先看项目结构，"));
        assertFalse(view.contains("  • 再给你改终端界面。"));
    }

    @Test
    void agentEventsMessageShouldSetErrorStatusForStreamedError() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        model.update(new AgentEventsMessage(List.of(
            AgentEvent.of("67890", "777", 1, AgentEventType.ERROR, Map.of(
                "message", "请先登录"
            ))
        )));

        String view = model.view();
        assertTrue(view.contains("! Error: 请先登录"));
        assertTrue(view.contains("error"));
    }

    /**
     * 按真实键盘路径把字符送进 textarea，避免测试绕过 TUI 输入事件处理顺序。
     */
    private static void pressRunes(CodingXTuiModel model, String value) {
        for (char rune : value.toCharArray()) {
            model.update(new KeyPressMessage(new Key(KeyType.KeyRunes, new char[]{rune})));
        }
    }

    /**
     * 断言键盘提交会通过普通屏幕打印命令展示用户输入，避免 live view 高度变化裁掉首条用户消息。
     */
    private static void assertPrintLine(UpdateResult<?> result, String expectedText) {
        assertNotNull(result.command());
        Message message = result.command().execute();
        PrintLineMessage printLineMessage = assertInstanceOf(PrintLineMessage.class, message);
        assertTrue(printLineMessage.messageBody().contains(expectedText));
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

    /**
     * 测试专用流式事件源，记录 TUI 提交时传入的 planMode。
     */
    private static class CapturingStreamingEventSource implements StreamingAgentEventSource {

        private final List<Boolean> planModes = new java.util.ArrayList<>();

        @Override
        public void startTurn(
            String task,
            Path workspace,
            boolean planMode,
            java.util.function.Consumer<AgentEvent> eventConsumer
        ) {
            planModes.add(planMode);
            eventConsumer.accept(AgentEvent.of("session", "turn", planModes.size(), AgentEventType.TURN_COMPLETED, Map.of(
                "status", "COMPLETED"
            )));
        }

        @Override
        public void startTurn(String task, Path workspace, java.util.function.Consumer<AgentEvent> eventConsumer) {
            startTurn(task, workspace, false, eventConsumer);
        }
    }
}
