package com.codingx.cli.tui;

import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.AgentEventSource;
import com.codingx.cli.agent.AgentEventType;
import com.codingx.cli.agent.MockAgentEventSource;
import com.codingx.cli.agent.StreamingAgentEventSource;
import com.codingx.cli.auth.CliAuthService;
import com.codingx.cli.render.TerminalRenderer;
import com.codingx.cli.slash.CliSlashCommand;
import com.codingx.cli.slash.SlashCommandCatalog;
import com.williamcallahan.tui4j.compat.bubbletea.KeyPressMessage;
import com.williamcallahan.tui4j.compat.bubbletea.PasteMessage;
import com.williamcallahan.tui4j.compat.bubbletea.Program;
import com.williamcallahan.tui4j.compat.bubbletea.ProgramOption;
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult;
import com.williamcallahan.tui4j.compat.bubbletea.WindowSizeMessage;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.Key;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyType;
import com.williamcallahan.tui4j.compat.lipgloss.color.NoColor;
import com.williamcallahan.tui4j.term.TerminalInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

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

        assertTrue(stripAnsi(view).contains("› █输入任务，/ 查看命令"));
        assertFalse(view.contains("Write tests for @filename"));
        assertTrue(view.contains("server selected ·"));
        assertFalse(view.contains(">_ CodingX CLI (v0.1.0)"));
        assertFalse(view.contains("Tip: Build faster with CodingX."));
        assertTrue(view.lines().count() <= 4);
    }

    @Test
    void composerShouldRenderVisibleBlinkingCursorCell() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        String view = stripAnsi(model.view());

        assertTrue(view.contains("› █输入任务，/ 查看命令"), view);
    }

    @Test
    void composerCursorShouldFollowLeftAndRightNavigation() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        // 回归自绘光标固定在输入末尾的问题：方向键只更新 Textarea，view 必须读取真实光标位置。
        pressRunes(model, "abc");
        assertTrue(stripAnsi(model.view()).contains("› abc█"), model.view());

        model.update(new KeyPressMessage(new Key(KeyType.KeyLeft)));
        assertTrue(stripAnsi(model.view()).contains("› ab█c"), model.view());

        model.update(new KeyPressMessage(new Key(KeyType.KeyRight)));
        assertTrue(stripAnsi(model.view()).contains("› abc█"), model.view());
    }

    @Test
    void composerCursorShouldFollowUpAndDownNavigationAcrossInputLines() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        // 粘贴多行文本后仍压平成单行 composer，但光标位置要跟随 Textarea 的真实行列移动。
        model.update(new PasteMessage("abc\ndef"));
        assertTrue(stripAnsi(model.view()).contains("› abc def█"), model.view());

        model.update(new KeyPressMessage(new Key(KeyType.KeyUp)));
        assertTrue(stripAnsi(model.view()).contains("› ab█c def"), model.view());

        model.update(new KeyPressMessage(new Key(KeyType.KeyDown)));
        assertTrue(stripAnsi(model.view()).contains("› abc de█f"), model.view());
    }

    @Test
    void assistantReplyShouldHaveSpacingAfterUserMessage() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        // 用户输入和助手回答之间保留空行，避免终端里两种说话者粘在一起难以区分。
        model.submitTask("分析这个项目");

        String view = stripAnsi(model.view());
        assertTrue(view.contains("› 分析这个项目\n\n• 我会先查看当前仓库结构"), view);
    }

    @Test
    void runningWorkingHintShouldRenderAsDimText() throws Exception {
        NonCompletingStreamingEventSource eventSource = new NonCompletingStreamingEventSource();
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer()
        );
        model.setProgram(new Program(model));

        // Working 只表达等待状态，应使用灰色弱提示，不能看起来像一条普通助手消息。
        pressRunes(model, "你是谁");
        model.update(new KeyPressMessage(new Key(KeyType.keyCR)));
        eventSource.awaitTaskCount(1);

        String view = model.view();
        assertTrue(view.contains("\u001B[90m• Working ("), view);
        assertTrue(view.contains("esc to interrupt)\u001B[0m"), view);
    }

    @Test
    void dimWorkingHintShouldIgnoreAnsiSequencesWhenWrapping() throws Exception {
        NonCompletingStreamingEventSource eventSource = new NonCompletingStreamingEventSource();
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer()
        );
        model.setProgram(new Program(model));
        model.update(new WindowSizeMessage(40, 8));

        pressRunes(model, "你是谁");
        model.update(new KeyPressMessage(new Key(KeyType.keyCR)));
        eventSource.awaitTaskCount(1);
        setRunningStartedAt(model, System.nanoTime() - TimeUnit.SECONDS.toNanos(10));

        // ANSI 灰色控制串不占终端列宽；窄屏下 Working 可见文本本身仍应保持在同一行。
        String view = stripAnsi(model.view());
        assertTrue(
            view.lines().anyMatch(line -> line.matches("• Working \\(\\d+s • esc to interrupt\\)")),
            view
        );
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
        assertTrue(view.contains("› 分析这个项目"));
        assertTrue(view.contains("我会先查看当前仓库结构"));
        assertTrue(view.contains("ToolSearch"));
        assertFalse(view.contains("Task completed: COMPLETED"));
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
        assertNull(result.command());
        assertTrue(view.contains("› hello"));
        assertInOrder(view, "› hello", "• 我会先查看当前仓库结构");
        assertTrue(view.contains("我会先查看当前仓库结构"));
        assertTrue(stripAnsi(view).contains("› █输入任务，/ 查看命令"));
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
        assertNull(result.command());
        assertTrue(view.contains("› hello"));
        assertInOrder(view, "› hello", "• 我会先查看当前仓库结构");
        assertTrue(view.contains("我会先查看当前仓库结构"));
        assertTrue(stripAnsi(view).contains("› █输入任务，/ 查看命令"));
    }

    @Test
    void consecutiveKeyboardSubmissionsShouldRenderQuestionAnswerPairsInOrder() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        pressRunes(model, "你好");
        model.update(new KeyPressMessage(new Key(KeyType.keyCR)));
        pressRunes(model, "你是人机吗");
        model.update(new KeyPressMessage(new Key(KeyType.keyCR)));

        assertInOrder(
            model.view(),
            "› 你好",
            "• 我会先查看当前仓库结构",
            "› 你是人机吗",
            "• 我会先查看当前仓库结构"
        );
    }

    @Test
    void runningStreamingTurnShouldKeepNextInputInComposerUntilCurrentTurnCompletes() throws Exception {
        NonCompletingStreamingEventSource eventSource = new NonCompletingStreamingEventSource();
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer()
        );
        model.setProgram(new Program(model));

        pressRunes(model, "first");
        model.update(new KeyPressMessage(new Key(KeyType.keyCR)));
        eventSource.awaitTaskCount(1);
        pressRunes(model, "second");
        model.update(new KeyPressMessage(new Key(KeyType.keyCR)));

        String view = model.view();
        assertEquals(List.of("first"), eventSource.tasks);
        assertTrue(view.contains("› first"));
        assertFalse(view.lines().anyMatch(line -> line.equals("› second")));
        assertTrue(view.contains("› second"));

        model.update(new AgentEventsMessage(List.of(
            AgentEvent.of("session", "turn", 1, AgentEventType.TURN_COMPLETED, Map.of(
                "status", "COMPLETED"
            ))
        )));
        model.update(new KeyPressMessage(new Key(KeyType.keyCR)));
        eventSource.awaitTaskCount(2);

        assertEquals(List.of("first", "second"), List.copyOf(eventSource.tasks));
        assertInOrder(model.view(), "› first", "› second");
        assertFalse(model.view().contains("Task completed: COMPLETED"));
    }

    @Test
    void runningStreamingAssistantDeltaShouldKeepSubmittedQuestionVisible() throws Exception {
        DelayedStreamingEventSource eventSource = new DelayedStreamingEventSource();
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer()
        );
        model.setProgram(new Program(model));

        pressRunes(model, "你好你好");
        model.update(new KeyPressMessage(new Key(KeyType.keyCR)));
        eventSource.awaitTaskCount(1);
        model.update(new AgentEventsMessage(List.of(
            AgentEvent.of("session", "turn", 1, AgentEventType.ASSISTANT_DELTA, Map.of(
                "delta", "你好你好，我正在处理。"
            ))
        )));

        String view = model.view();
        assertInOrder(view, "› 你好你好", "• 你好你好，我正在处理。");
        assertTrue(view.contains("running"), view);
    }

    @Test
    void runningTurnShouldRenderElapsedWorkingHintAndEscInterruptHelp() throws Exception {
        NonCompletingStreamingEventSource eventSource = new NonCompletingStreamingEventSource();
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer()
        );
        model.setProgram(new Program(model));

        pressRunes(model, "你是谁");
        model.update(new KeyPressMessage(new Key(KeyType.keyCR)));
        eventSource.awaitTaskCount(1);

        String view = stripAnsi(model.view());
        assertTrue(view.contains("› 你是谁"), view);
        assertTrue(view.matches("(?s).*• Working \\(\\d+s • esc to interrupt\\).*"), view);
    }

    @Test
    void escShouldInterruptRunningTurnInsteadOfQuittingProgram() throws Exception {
        NonCompletingStreamingEventSource eventSource = new NonCompletingStreamingEventSource();
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer()
        );
        model.setProgram(new Program(model));

        pressRunes(model, "你是谁");
        model.update(new KeyPressMessage(new Key(KeyType.keyCR)));
        eventSource.awaitTaskCount(1);
        UpdateResult<?> result = model.update(new KeyPressMessage(new Key(KeyType.keyESC)));

        String view = stripAnsi(model.view());
        assertNull(result.command());
        assertTrue(view.contains("• Interrupted"), view);
        assertTrue(view.contains("interrupted"), view);
        assertFalse(view.contains("Working ("), view);
    }

    @Test
    void runningStreamingAssistantDeltaShouldKeepQuestionNearComposer() throws Exception {
        DelayedStreamingEventSource eventSource = new DelayedStreamingEventSource();
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer()
        );
        model.setProgram(new Program(model));

        pressRunes(model, "你好你好");
        model.update(new KeyPressMessage(new Key(KeyType.keyCR)));
        eventSource.awaitTaskCount(1);
        model.update(new AgentEventsMessage(List.of(
            AgentEvent.of("session", "turn", 1, AgentEventType.ASSISTANT_DELTA, Map.of(
                "delta", "你好你好，我正在处理。"
            ))
        )));

        String bottomArea = lastLines(stripAnsi(model.view()), 6);
        assertTrue(bottomArea.contains("› 你好你好"), bottomArea);
        assertTrue(bottomArea.contains("› █输入任务，/ 查看命令"), bottomArea);
    }

    @Test
    void completedTurnShouldKeepQuestionOnlyInTranscript() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        model.submitTask("完成后也要看见");

        String view = stripAnsi(model.view());
        assertEquals(1, countOccurrences(view, "› 完成后也要看见"), view);
        assertTrue(view.contains("› █输入任务，/ 查看命令"), view);
        assertTrue(view.contains("completed"), view);
    }

    @Test
    void completedShortTurnShouldNotDuplicateQuestionAboveComposer() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        model.submitTask("不要重复显示");

        String view = stripAnsi(model.view());
        assertEquals(1, countOccurrences(view, "› 不要重复显示"), view);
        assertFalse(lastLines(view, 3).contains("› 不要重复显示"), view);
    }

    @Test
    void emptyComposerShouldRenderPlaceholderAsDimText() {
        TerminalInfo.provide(() -> new TerminalInfo(true, new NoColor()));
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        String view = model.view();

        assertTrue(view.contains("› █\u001B[90m输入任务，/ 查看命令\u001B[0m"), view);
    }

    @Test
    void rendererBottomThreeLinesShouldKeepOnlyComposerAndStatusForShortTurn() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        model.submitTask("三行内也要看见");

        // 短回答不在 composer 上方重复展示问题；问题只保留在 transcript 中。
        String bottomArea = lastLines(stripAnsi(model.view()), 3);
        assertFalse(bottomArea.contains("› 三行内也要看见"), bottomArea);
        assertTrue(bottomArea.contains("› █输入任务，/ 查看命令"), bottomArea);
        assertTrue(bottomArea.contains("completed"), bottomArea);
    }

    @Test
    void rendererViewShouldUseLineFeedOnly() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        model.submitTask("不要把回车符交给 renderer");

        // tui4j 的 RendererFlush 只按 LF 拆分逻辑行；Windows CRLF 会把 \r 留在行尾并破坏差量刷新。
        assertFalse(model.view().contains("\r"), model.view());
    }

    @Test
    void ttyComposerShouldRenderPlaceholderWithoutDuplicatingQuestion() {
        TerminalInfo.provide(() -> new TerminalInfo(true, new NoColor()));
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer()
        );

        model.submitTask("真实 TTY 也要看见");

        // 真实终端下 placeholder 是灰色提示，不应该再把当前问题复制到输入区上方。
        String rawView = model.view();
        String bottomArea = lastLines(stripAnsi(rawView), 3);
        assertFalse(bottomArea.contains("› 真实 TTY 也要看见"), bottomArea);
        assertTrue(rawView.contains("› █\u001B[90m输入任务，/ 查看命令\u001B[0m"), rawView);
        assertTrue(bottomArea.contains("› █输入任务，/ 查看命令"), bottomArea);
        assertTrue(bottomArea.contains("completed"), bottomArea);
    }

    @Test
    void realProgramStreamingShouldRenderSubmittedQuestionWhileRunning() throws Exception {
        ProgramStreamingEventSource eventSource = new ProgramStreamingEventSource();
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer()
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PipedInputStream input = new PipedInputStream();
        PipedOutputStream inputWriter = new PipedOutputStream(input);
        Program program = new Program(
            model,
            ProgramOption.withInput(input),
            ProgramOption.withOutput(output),
            ProgramOption.withoutSignalHandler(),
            ProgramOption.withoutBracketedPaste()
        );
        model.setProgram(program);
        Thread programThread = new Thread(program::run, "codingx-tui-program-test");
        programThread.start();

        inputWriter.write("visible-user\r".getBytes(StandardCharsets.UTF_8));
        inputWriter.flush();
        eventSource.awaitTaskCount(1);
        awaitContains(() -> lastLines(model.view(), 6), "› visible-user");
        awaitContains(() -> stripAnsi(output.toString(StandardCharsets.UTF_8)), "› visible-user");

        inputWriter.write(3);
        inputWriter.flush();
        programThread.join(1000);
        assertFalse(programThread.isAlive(), "Program should quit after ctrl+c");
    }

    @Test
    void currentQuestionShouldStayVisibleWhenLongAnswerOverflowsTerminal() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new LongAnswerEventSource(),
            new TerminalRenderer()
        );

        model.update(new WindowSizeMessage(80, 12));
        model.submitTask("看不见啊");

        String visibleTerminal = lastLines(model.view(), 12);
        assertTrue(visibleTerminal.contains("› 看不见啊"), visibleTerminal);
        assertTrue(visibleTerminal.contains("long-output-29"), visibleTerminal);
    }

    @Test
    void currentQuestionShouldStayVisibleWhenSingleLongLineWrapsInTerminal() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new WrappedLongLineEventSource(),
            new TerminalRenderer()
        );

        model.update(new WindowSizeMessage(40, 12));
        model.submitTask("长行也要看见");

        String visibleTerminal = lastVisualLines(model.view(), 40, 12);
        assertTrue(visibleTerminal.contains("› 长行也要看见"), visibleTerminal);
        assertTrue(visibleTerminal.contains("wrap-output-"), visibleTerminal);
    }

    @Test
    void longBackendOutputShouldBePreWrappedBeforeStandardRendererFlushes() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new WrappedLongLineEventSource(),
            new TerminalRenderer()
        );

        model.update(new WindowSizeMessage(40, 1000));
        model.submitTask("真实终端也要看见");

        List<String> overWideLines = model.view().lines()
            .filter(line -> line.length() > 40)
            .toList();
        assertTrue(overWideLines.isEmpty(), String.join(System.lineSeparator(), overWideLines));
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
    void typingSlashShouldShowCliCommandListWithoutSubmittingChatRequest() {
        CapturingStreamingEventSource eventSource = new CapturingStreamingEventSource();
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer(),
            null,
            backendSlashCatalog()
        );

        pressRunes(model, "/");

        String view = model.view();
        assertTrue(view.contains("/login"), view);
        assertTrue(view.contains("/logout"), view);
        assertTrue(view.contains("/review"), view);
        assertTrue(view.contains("/fix-test"), view);
        assertTrue(view.contains("审查当前改动"), view);
        assertTrue(view.contains("登录 CodingX"), view);
        assertTrue(view.contains("\u001B[90m  命令"), view);
        assertTrue(view.contains("定位并修复测试失败\u001B[0m"), view);
        assertFalse(view.contains("\u001B[90m› /"), view);
        assertTrue(view.contains("› /"), view);
        assertTrue(eventSource.tasks.isEmpty());
    }

    @Test
    void typingSlashKeywordShouldFilterBackendGovernanceCommands() {
        CapturingStreamingEventSource eventSource = new CapturingStreamingEventSource();
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer(),
            null,
            backendSlashCatalog()
        );

        pressRunes(model, "/fi");

        String view = model.view();
        assertTrue(view.contains("/fix-test"), view);
        assertFalse(view.contains("/review"), view);
        assertFalse(view.contains("/login"), view);
        assertTrue(eventSource.tasks.isEmpty());
    }

    @Test
    void tabShouldCompleteUniqueSlashCommandMatch() {
        CapturingStreamingEventSource eventSource = new CapturingStreamingEventSource();
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer(),
            null,
            backendSlashCatalog()
        );

        pressRunes(model, "/logi");
        model.update(new KeyPressMessage(new Key(KeyType.keyHT)));

        String view = stripAnsi(model.view());
        assertTrue(view.contains("› /login█"), view);
        assertFalse(view.contains("› /logi█"), view);
        assertTrue(eventSource.tasks.isEmpty());
    }

    @Test
    void tabShouldSelectFirstSlashCommandWhenMultipleMatches() {
        CapturingStreamingEventSource eventSource = new CapturingStreamingEventSource();
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer(),
            null,
            backendSlashCatalog()
        );

        pressRunes(model, "/log");
        model.update(new KeyPressMessage(new Key(KeyType.keyHT)));

        String view = stripAnsi(model.view());
        assertTrue(view.contains("› /login█"), view);
        assertFalse(view.contains("› /log█"), view);
        assertTrue(eventSource.tasks.isEmpty());
    }

    @Test
    void downArrowShouldMoveSelectionToNextSlashCommand() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer(),
            null,
            backendSlashCatalog()
        );

        pressRunes(model, "/");
        model.update(new KeyPressMessage(new Key(KeyType.KeyDown)));

        String view = stripAnsi(model.view());
        // 第二项 /logout 变为选中态，带选择箭头标记。
        assertTrue(view.contains("› /logout"), view);
        // 第一项 /login 不再是选中态（无选择箭头），但仍在面板中。
        assertTrue(view.contains("/login"), view);
    }

    @Test
    void upArrowFromFirstItemShouldWrapToLastSlashCommand() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer(),
            null,
            backendSlashCatalog()
        );

        pressRunes(model, "/");
        // 初始选中第一项，按 Up 应回绕到最后一项。
        model.update(new KeyPressMessage(new Key(KeyType.KeyUp)));

        String view = stripAnsi(model.view());
        // 5 个命令（login/logout/help/review/fix-test），最后一项是 /fix-test。
        assertTrue(view.contains("› /fix-test"), view);
    }

    @Test
    void enterShouldSelectHighlightedSlashCommand() {
        CapturingStreamingEventSource eventSource = new CapturingStreamingEventSource();
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer(),
            null,
            backendSlashCatalog()
        );

        pressRunes(model, "/");
        // 下移两项选中 /help（索引 2），然后回车选中。
        model.update(new KeyPressMessage(new Key(KeyType.KeyDown)));
        model.update(new KeyPressMessage(new Key(KeyType.KeyDown)));
        model.update(new KeyPressMessage(new Key(KeyType.keyCR)));

        String view = stripAnsi(model.view());
        assertTrue(view.contains("› /help█"), view);
        assertTrue(eventSource.tasks.isEmpty(), "命令面板选中不应提交任务");
    }

    @Test
    void slashCommandPanelShouldRenderUnselectedCommandsDimly() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer(),
            null,
            backendSlashCatalog()
        );

        pressRunes(model, "/");

        String view = model.view();
        // 未选中命令保持灰色弱提示，只让当前选中项抢占视觉焦点。
        assertTrue(view.contains("\u001B[90m  /logout 退出登录\u001B[0m"), view);
        assertTrue(view.contains("\u001B[90m  /review 审查当前改动并优先指出风险和测试缺口\u001B[0m"), view);
        // 选中项使用蓝色前景 + 灰色背景，避免整张命令列表都像高亮信息。
        assertTrue(view.contains("\u001B[34;48;5;238m/login\u001B[0m"), view);
        assertTrue(view.contains("登录 CodingX"), view);
    }

    @Test
    void selectedSlashCommandShouldUseBlueOnGrayBackground() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer(),
            null,
            backendSlashCatalog()
        );

        pressRunes(model, "/");
        // 初始选中第一项 /login。
        String view = model.view();
        assertTrue(view.contains("\u001B[34;48;5;238m/login"), view);
    }

    @Test
    void typingAfterNavigationShouldResetSlashCommandSelection() {
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            new MockAgentEventSource(),
            new TerminalRenderer(),
            null,
            backendSlashCatalog()
        );

        pressRunes(model, "/");
        // 下移两项后先选中 /help，继续输入会触发过滤并把选中索引重置到第一项。
        model.update(new KeyPressMessage(new Key(KeyType.KeyDown)));
        model.update(new KeyPressMessage(new Key(KeyType.KeyDown)));
        // 继续输入字符，选中索引应重置为 0。
        pressRunes(model, "r");

        String view = stripAnsi(model.view());
        // 过滤后第一项 /review 应为选中态。
        assertTrue(view.contains("› /review"), view);
        // /login 和 /logout 已被过滤掉。
        assertFalse(view.contains("/login"), view);
        assertFalse(view.contains("/logout"), view);
    }

    @Test
    void backendSlashCommandShouldSubmitChatInsteadOfLocalUnknownCommand() {
        CapturingStreamingEventSource eventSource = new CapturingStreamingEventSource();
        FakeCliAuthService authService = new FakeCliAuthService();
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer(),
            authService,
            backendSlashCatalog()
        );

        pressRunes(model, "/review 请审查当前改动");
        model.update(new KeyPressMessage(new Key(KeyType.keyCR)));

        String view = model.view();
        assertEquals(List.of("/review 请审查当前改动"), eventSource.tasks);
        assertFalse(view.contains("未知命令"), view);
        assertTrue(view.contains("› /review 请审查当前改动"), view);
    }

    @Test
    void slashLoginShouldUseCliAuthWithoutSubmittingChatRequest() {
        CapturingStreamingEventSource eventSource = new CapturingStreamingEventSource();
        FakeCliAuthService authService = new FakeCliAuthService();
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer(),
            authService
        );

        pressRunes(model, "/login");
        model.update(new KeyPressMessage(new Key(KeyType.keyCR)));

        String view = model.view();
        assertEquals(1, authService.browserLoginCount);
        assertTrue(eventSource.tasks.isEmpty());
        assertTrue(view.contains("CLI 登录成功"), view);
        assertTrue(view.contains("completed"), view);
    }

    @Test
    void slashLoginShouldNotBlockEscapeQuitWhileBrowserCallbackIsPending() throws Exception {
        CapturingStreamingEventSource eventSource = new CapturingStreamingEventSource();
        BlockingCliAuthService authService = new BlockingCliAuthService();
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer(),
            authService
        );
        model.setProgram(new Program(model));

        pressRunes(model, "/login");
        CompletableFuture<UpdateResult<?>> loginUpdate = CompletableFuture.supplyAsync(
            () -> model.update(new KeyPressMessage(new Key(KeyType.keyCR)))
        );
        try {
            authService.awaitBrowserLoginStarted();

            UpdateResult<?> loginResult = loginUpdate.get(500, TimeUnit.MILLISECONDS);
            assertNull(loginResult.command());
            assertTrue(eventSource.tasks.isEmpty());
            assertTrue(stripAnsi(model.view()).contains("正在打开浏览器登录 CodingX"), model.view());

            UpdateResult<?> escapeResult = model.update(new KeyPressMessage(new Key(KeyType.keyESC)));

            assertTrue(authService.awaitBrowserLoginInterrupted(), "后台登录等待应被 Esc 取消");
            assertTrue(escapeResult.command().execute() instanceof com.williamcallahan.tui4j.compat.bubbletea.QuitMessage);
        } finally {
            authService.releaseBrowserLogin();
        }
    }

    @Test
    void slashLogoutShouldUseCliAuthWithoutSubmittingChatRequest() {
        CapturingStreamingEventSource eventSource = new CapturingStreamingEventSource();
        FakeCliAuthService authService = new FakeCliAuthService();
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer(),
            authService
        );

        pressRunes(model, "/logout");
        model.update(new KeyPressMessage(new Key(KeyType.keyCR)));

        String view = model.view();
        assertEquals(1, authService.logoutCount);
        assertTrue(eventSource.tasks.isEmpty());
        assertTrue(view.contains("CLI 已退出登录"), view);
        assertTrue(view.contains("completed"), view);
    }

    @Test
    void normalTaskShouldOpenLoginBeforeSubmittingWhenCliTokenMissing() {
        CapturingStreamingEventSource eventSource = new CapturingStreamingEventSource();
        FakeCliAuthService authService = new FakeCliAuthService(false);
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer(),
            authService
        );

        pressRunes(model, "分析当前项目");
        model.update(new KeyPressMessage(new Key(KeyType.keyCR)));

        String view = model.view();
        assertEquals(1, authService.browserLoginCount);
        assertEquals(List.of("分析当前项目"), eventSource.tasks);
        assertTrue(view.contains("CLI 登录成功"), view);
        assertTrue(view.contains("› 分析当前项目"), view);
    }

    @Test
    void normalTaskShouldNotSubmitWhenAutomaticLoginFails() {
        CapturingStreamingEventSource eventSource = new CapturingStreamingEventSource();
        FakeCliAuthService authService = new FakeCliAuthService(false, false);
        CodingXTuiModel model = new CodingXTuiModel(
            tempDir.resolve("workspace"),
            eventSource,
            new TerminalRenderer(),
            authService
        );

        pressRunes(model, "分析当前项目");
        model.update(new KeyPressMessage(new Key(KeyType.keyCR)));

        String view = model.view();
        assertEquals(1, authService.browserLoginCount);
        assertTrue(eventSource.tasks.isEmpty());
        assertTrue(view.contains("CLI 登录失败"), view);
        assertTrue(view.contains("error"), view);
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
        assertFalse(view.contains("Task completed: COMPLETED"));
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
     * 测试专用 Slash Command 目录，模拟管理端治理中心维护并由后端用户接口返回的启用命令。
     */
    private static SlashCommandCatalog backendSlashCatalog() {
        return () -> List.of(
            new CliSlashCommand("206060101", "review", "/review", "审查当前改动并优先指出风险和测试缺口", "BUILTIN", 1),
            new CliSlashCommand("206060102", "fix-test", "/fix-test", "定位并修复测试失败", "BUILTIN", 2)
        );
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

        private final List<String> tasks = new java.util.ArrayList<>();

        private final List<Boolean> planModes = new java.util.ArrayList<>();

        @Override
        public void startTurn(
            String task,
            Path workspace,
            boolean planMode,
            java.util.function.Consumer<AgentEvent> eventConsumer
        ) {
            tasks.add(task);
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

    /**
     * 测试专用认证服务，只记录 TUI 内部命令调用次数，避免单测打开真实浏览器。
     */
    private static class FakeCliAuthService extends CliAuthService {

        /**
         * 浏览器登录调用次数。
         */
        private int browserLoginCount;

        /**
         * 退出登录调用次数。
         */
        private int logoutCount;

        /**
         * 是否已有可用登录态。
         */
        private final boolean loggedIn;

        /**
         * 浏览器登录是否成功。
         */
        private final boolean browserLoginResult;

        FakeCliAuthService() {
            this(true);
        }

        FakeCliAuthService(boolean loggedIn) {
            this(loggedIn, true);
        }

        FakeCliAuthService(boolean loggedIn, boolean browserLoginResult) {
            super(null, null, ignored -> {
            });
            this.loggedIn = loggedIn;
            this.browserLoginResult = browserLoginResult;
        }

        @Override
        public boolean isLoggedIn() {
            return loggedIn;
        }

        @Override
        public boolean loginWithBrowser() {
            browserLoginCount++;
            return browserLoginResult;
        }

        @Override
        public boolean logout() {
            logoutCount++;
            return true;
        }
    }

    /**
     * 模拟浏览器回调迟迟不返回的登录服务，用于验证 TUI 主循环不会被登录流程卡死。
     */
    private static class BlockingCliAuthService extends CliAuthService {

        private final CountDownLatch browserLoginStarted = new CountDownLatch(1);

        private final CountDownLatch browserLoginInterrupted = new CountDownLatch(1);

        private final CountDownLatch browserLoginReleased = new CountDownLatch(1);

        BlockingCliAuthService() {
            super(null, null, ignored -> {
            });
        }

        @Override
        public boolean loginWithBrowser() {
            browserLoginStarted.countDown();
            try {
                browserLoginReleased.await();
                return true;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                browserLoginInterrupted.countDown();
                return false;
            }
        }

        private void awaitBrowserLoginStarted() throws InterruptedException, TimeoutException {
            if (!browserLoginStarted.await(1, TimeUnit.SECONDS)) {
                throw new TimeoutException("浏览器登录未启动");
            }
        }

        private boolean awaitBrowserLoginInterrupted() throws InterruptedException {
            return browserLoginInterrupted.await(1, TimeUnit.SECONDS);
        }

        private void releaseBrowserLogin() {
            browserLoginReleased.countDown();
        }
    }

    /**
     * 测试运行中的后端流：只记录已发起任务，不回推完成事件，从而让 TUI 保持 running 状态。
     */
    private static class NonCompletingStreamingEventSource implements StreamingAgentEventSource {

        private final List<String> tasks = new CopyOnWriteArrayList<>();

        @Override
        public void startTurn(
            String task,
            Path workspace,
            boolean planMode,
            java.util.function.Consumer<AgentEvent> eventConsumer
        ) {
            tasks.add(task);
        }

        @Override
        public void startTurn(String task, Path workspace, java.util.function.Consumer<AgentEvent> eventConsumer) {
            startTurn(task, workspace, false, eventConsumer);
        }

        /**
         * 等待后台流线程收到指定数量的任务，避免测试在异步提交前断言。
         */
        protected void awaitTaskCount(int expectedCount) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (tasks.size() < expectedCount && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(10);
            }
            assertEquals(expectedCount, tasks.size());
        }
    }

    /**
     * 测试运行中的后端流：先只记录任务，测试再手动推送流式回答，覆盖 running 状态下的真实可见性。
     */
    private static class DelayedStreamingEventSource extends NonCompletingStreamingEventSource {
    }

    /**
     * 真实 Program.run 测试使用的流式事件源：收到任务后立即通过 consumer 推送回答增量，但不结束当前轮。
     */
    private static class ProgramStreamingEventSource extends NonCompletingStreamingEventSource {

        @Override
        public void startTurn(
            String task,
            Path workspace,
            boolean planMode,
            java.util.function.Consumer<AgentEvent> eventConsumer
        ) {
            super.startTurn(task, workspace, planMode, eventConsumer);
            eventConsumer.accept(AgentEvent.of("session", "turn", 1, AgentEventType.ASSISTANT_DELTA, Map.of(
                "delta", "streaming answer"
            )));
        }
    }

    /**
     * 构造超长回答，复现普通终端按最后 N 行裁剪时用户问题被顶出可见区的问题。
     */
    private static class LongAnswerEventSource implements AgentEventSource {

        @Override
        public List<AgentEvent> startTurn(String task, Path workspace) {
            String output = String.join(System.lineSeparator(), IntStream.range(0, 30)
                .mapToObj(index -> "long-output-" + index)
                .toList());
            return List.of(
                AgentEvent.of("session", "turn", 1, AgentEventType.ASSISTANT_DELTA, Map.of(
                    "delta", "我先确认问题。"
                )),
                AgentEvent.of("session", "turn", 2, AgentEventType.COMMAND_OUTPUT_DELTA, Map.of(
                    "delta", output
                )),
                AgentEvent.of("session", "turn", 3, AgentEventType.TURN_COMPLETED, Map.of(
                    "status", "COMPLETED"
                ))
            );
        }
    }

    /**
     * 构造不含换行但足够长的回答，复现真实终端自动换行后用户问题被挤出可见区的情况。
     */
    private static class WrappedLongLineEventSource implements AgentEventSource {

        @Override
        public List<AgentEvent> startTurn(String task, Path workspace) {
            String output = "wrap-output-".repeat(80);
            return List.of(
                AgentEvent.of("session", "turn", 1, AgentEventType.ASSISTANT_DELTA, Map.of(
                    "delta", output
                )),
                AgentEvent.of("session", "turn", 2, AgentEventType.TURN_COMPLETED, Map.of(
                    "status", "COMPLETED"
                ))
            );
        }
    }

    /**
     * 断言多个片段按顺序出现在同一份 view 中，专门防止用户问题和助手回答被拆到不同渲染区域。
     */
    private static void assertInOrder(String view, String... fragments) {
        int cursor = -1;
        for (String fragment : fragments) {
            int index = view.indexOf(fragment, cursor + 1);
            assertTrue(index > cursor, () -> "fragment not in order: " + fragment + System.lineSeparator() + view);
            cursor = index;
        }
    }

    /**
     * 模拟 tui4j 普通 renderer 在视图高度超过终端高度时保留最后 N 行的可见结果。
     */
    private static String lastLines(String view, int lineCount) {
        List<String> lines = view.lines().toList();
        int fromIndex = Math.max(lines.size() - lineCount, 0);
        return String.join(System.lineSeparator(), lines.subList(fromIndex, lines.size()));
    }

    /**
     * 按终端宽度估算自动换行后的最后 N 个视觉行，避免长单行在测试里被当成一行。
     */
    private static String lastVisualLines(String view, int width, int lineCount) {
        List<String> visualLines = new java.util.ArrayList<>();
        for (String line : view.lines().toList()) {
            int cursor = 0;
            StringBuilder visualLine = new StringBuilder();
            for (int offset = 0; offset < line.length(); ) {
                int codePoint = line.codePointAt(offset);
                int charWidth = Character.isISOControl(codePoint) ? 0 : 1;
                if (cursor + charWidth > width && !visualLine.isEmpty()) {
                    visualLines.add(visualLine.toString());
                    visualLine = new StringBuilder();
                    cursor = 0;
                }
                visualLine.appendCodePoint(codePoint);
                cursor += charWidth;
                offset += Character.charCount(codePoint);
            }
            visualLines.add(visualLine.toString());
        }
        int fromIndex = Math.max(visualLines.size() - lineCount, 0);
        return String.join(System.lineSeparator(), visualLines.subList(fromIndex, visualLines.size()));
    }

    /**
     * 等待异步 Program.run 渲染或状态更新出现指定内容，避免用固定 sleep 造成不稳定测试。
     */
    private static void awaitContains(java.util.function.Supplier<String> supplier, String expected)
        throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        String current = supplier.get();
        while (!current.contains(expected) && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
            current = supplier.get();
        }
        assertTrue(current.contains(expected), current);
    }

    private static void setRunningStartedAt(CodingXTuiModel model, long startedAtNanos) throws Exception {
        // 计时字段没有公开 setter；测试只调整起始时间来稳定复现两位数秒数的换行边界。
        var field = CodingXTuiModel.class.getDeclaredField("runningTurnStartedAtNanos");
        field.setAccessible(true);
        field.setLong(model, startedAtNanos);
    }

    /**
     * 去除真实 renderer 输出中的 ANSI 控制序列，便于断言可见文本。
     */
    private static String stripAnsi(String value) {
        return value
            .replaceAll("\\u001B\\[[;?0-9]*[ -/]*[@-~]", "")
            .replaceAll("\\u001B\\][^\\u0007]*\\u0007", "");
    }

    /**
     * 统计片段出现次数，用于避免用户问题同时出现在 transcript 和输入框锚点。
     */
    private static int countOccurrences(String value, String fragment) {
        int count = 0;
        int cursor = 0;
        while (cursor >= 0) {
            cursor = value.indexOf(fragment, cursor);
            if (cursor >= 0) {
                count++;
                cursor += fragment.length();
            }
        }
        return count;
    }
}
