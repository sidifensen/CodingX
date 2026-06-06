package com.codingx.cli.tui;

import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.AgentEventSource;
import com.codingx.cli.agent.AgentEventType;
import com.codingx.cli.agent.StreamingAgentEventSource;
import com.codingx.cli.render.TerminalRenderer;
import com.williamcallahan.tui4j.compat.bubbletea.Command;
import com.williamcallahan.tui4j.compat.bubbletea.KeyPressMessage;
import com.williamcallahan.tui4j.compat.bubbletea.Message;
import com.williamcallahan.tui4j.compat.bubbletea.Model;
import com.williamcallahan.tui4j.compat.bubbletea.Program;
import com.williamcallahan.tui4j.compat.bubbletea.QuitMessage;
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult;
import com.williamcallahan.tui4j.compat.bubbletea.WindowSizeMessage;
import com.williamcallahan.tui4j.compat.bubbletea.input.key.KeyType;
import com.williamcallahan.tui4j.compat.bubbles.cursor.Cursor;
import com.williamcallahan.tui4j.compat.bubbles.textarea.Textarea;
import com.williamcallahan.tui4j.compat.bubbles.viewport.Viewport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CodingX TUI 的 Bubble Tea 模型，维护启动卡片、transcript、输入框和状态栏。
 */
public class CodingXTuiModel implements Model {

    /**
     * 页面分区之间的空行间隔。
     */
    private static final String GAP = System.lineSeparator() + System.lineSeparator();

    /**
     * 当前 CLI 工作区，真实后端聊天流会把它作为 local runtime 的 repositoryPath。
     */
    private final Path workspace;

    /**
     * Agent 事件来源；生产环境是后端聊天流客户端，测试环境可替换为本地 mock。
     */
    private final AgentEventSource eventSource;

    /**
     * 顶部品牌区渲染器。
     */
    private final TuiHeaderRenderer headerRenderer;

    /**
     * 截图风格 transcript 渲染器。
     */
    private final TuiTranscriptRenderer transcriptRenderer;

    /**
     * 底部状态栏渲染器。
     */
    private final TuiStatusBarRenderer statusBarRenderer;

    /**
     * 滚动事件窗口，用于承载助手输出、工具状态和命令输出。
     */
    private final Viewport viewport;

    /**
     * 多行任务输入框，用户只能在 TUI 里提交任务。
     */
    private final Textarea textarea;

    /**
     * 已渲染的事件行；每次提交任务后追加并同步到 viewport。
     */
    private final List<String> timelineLines;

    /**
     * 后端流消费线程池；真实 SSE 读取不能阻塞 tui4j 主更新循环。
     */
    private final ExecutorService streamExecutor;

    /**
     * tui4j Program 引用，用于后台 SSE 线程把事件送回主更新循环。
     */
    private Program program;

    /**
     * 当前助手流式回答所在 transcript 行，连续 ASSISTANT_DELTA 会更新这一行而不是追加列表。
     */
    private int activeAssistantLineIndex;

    /**
     * 当前助手流式回答累积文本；工具、完成、错误等事件会结束该活动块。
     */
    private StringBuilder activeAssistantText;

    /**
     * 本地计划模式开关，会随任务提交传给后端聊天流。
     */
    private boolean planMode;

    /**
     * 当前运行状态，展示在底部状态栏。
     */
    private String status;

    /**
     * @param workspace 当前 CLI 工作区。
     * @param eventSource Agent 事件来源。
     * @param renderer 兼容旧构造链路的终端渲染器；截图风格 TUI 使用独立 transcript renderer。
     */
    public CodingXTuiModel(Path workspace, AgentEventSource eventSource, TerminalRenderer renderer) {
        this.workspace = workspace;
        this.eventSource = eventSource;
        this.headerRenderer = new TuiHeaderRenderer();
        this.transcriptRenderer = new TuiTranscriptRenderer();
        this.statusBarRenderer = new TuiStatusBarRenderer();
        this.viewport = Viewport.create(80, 1);
        this.textarea = new Textarea();
        this.timelineLines = new ArrayList<>();
        this.streamExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "codingx-tui-stream");
            thread.setDaemon(true);
            return thread;
        });
        this.planMode = true;
        this.status = "ready";
        this.activeAssistantLineIndex = -1;
        this.activeAssistantText = new StringBuilder();

        configureTextarea();
    }

    /**
     * 配置底部输入框的基础体验；真实任务只能从这里提交，所以保持提示、宽度和焦点稳定。
     */
    private void configureTextarea() {
        textarea.setPlaceholder("Write tests for @filename");
        textarea.setPrompt("› ");
        textarea.setCharLimit(1000);
        textarea.setWidth(80);
        textarea.setHeight(1);
        textarea.setShowLineNumbers(false);
        textarea.focus();
    }

    /**
     * 初始化 TUI 光标闪烁。
     *
     * @return 光标闪烁命令。
     */
    @Override
    public Command init() {
        return Cursor::blink;
    }

    /**
     * 注入 tui4j Program；后台流线程只能通过 Program.send 回到主线程更新 TUI 状态。
     *
     * @param program 当前运行中的 tui4j Program。
     */
    public void setProgram(Program program) {
        this.program = program;
    }

    /**
     * 处理按键、窗口尺寸和子组件事件。
     *
     * @param msg tui4j 消息。
     * @return 更新后的模型和后续命令。
     */
    @Override
    public UpdateResult<? extends Model> update(Message msg) {
        if (msg instanceof AgentEventsMessage agentEventsMessage) {
            appendAgentEvents(agentEventsMessage.events());
            return UpdateResult.from(this, null);
        }

        if (msg instanceof WindowSizeMessage windowSizeMessage) {
            resize(windowSizeMessage.width(), windowSizeMessage.height());
        }

        if (msg instanceof KeyPressMessage keyPressMessage) {
            String key = keyPressMessage.key();
            if ("ctrl+c".equals(key) || "esc".equals(key)) {
                streamExecutor.shutdownNow();
                return UpdateResult.from(this, QuitMessage::new);
            }
            if ("shift+tab".equals(key) || keyPressMessage.type() == KeyType.KeyShiftTab) {
                planMode = !planMode;
                return UpdateResult.from(this, null);
            }
            if (isSubmitKey(keyPressMessage)) {
                // 提交键必须先于 textarea.update() 处理；否则 LF/CR 会被 textarea 当成编辑换行，导致任务不进入 transcript。
                String normalizedTask = normalizeTask(textarea.value());
                if (normalizedTask.isEmpty()) {
                    return UpdateResult.from(this, null);
                }
                if (isTurnRunning()) {
                    // 当前轮还在流式输出时保留输入框内容，避免多条问题先堆出来、回答后到而破坏一问一答顺序。
                    return UpdateResult.from(this, null);
                }
                submitTask(normalizedTask);
                textarea.reset();
                return UpdateResult.from(this, null);
            }
        }

        UpdateResult<? extends Model> viewportResult = viewport.update(msg);
        UpdateResult<? extends Model> textareaResult = textarea.update(msg);
        Command command = combine(viewportResult.command(), textareaResult.command());
        return UpdateResult.from(this, command);
    }

    /**
     * 判断当前按键是否应提交任务；Windows/JLine 可能把回车解析为 LF(ctrl+j)，不能只识别 CR(enter)。
     *
     * @param keyPressMessage 键盘事件。
     * @return true 表示提交当前输入框内容。
     */
    private boolean isSubmitKey(KeyPressMessage keyPressMessage) {
        return "enter".equals(keyPressMessage.key())
            || keyPressMessage.type() == KeyType.keyCR
            || keyPressMessage.type() == KeyType.keyLF;
    }

    /**
     * 根据终端尺寸重算 transcript 和输入框布局；启动页保持紧凑，不用空白 viewport 撑满屏幕。
     *
     * @param width 当前终端宽度。
     * @param height 当前终端高度。
     */
    private void resize(int width, int height) {
        int safeWidth = Math.max(width, 40);
        int viewportHeight = Math.max(Math.min(timelineLines.size(), height - 10), 1);
        viewport.setWidth(safeWidth);
        viewport.setHeight(viewportHeight);
        textarea.setWidth(safeWidth);
        textarea.setHeight(1);
        refreshViewport();
    }

    /**
     * 提交任务并把返回事件追加到 TUI 时间线；测试直接调用该方法避免启动真实终端。
     *
     * @param task 用户任务。
     */
    public void submitTask(String task) {
        String normalizedTask = normalizeTask(task);
        if (normalizedTask.isEmpty()) {
            return;
        }

        status = "running";
        appendUserLine(normalizedTask);
        if (eventSource instanceof StreamingAgentEventSource streamingEventSource && program != null) {
            // 真实后端 SSE 在后台线程消费；每个事件通过 Program.send 回到 update()，避免跨线程直接改 UI 状态。
            streamExecutor.submit(() -> streamingEventSource.startTurn(
                normalizedTask,
                workspace,
                planMode,
                event -> program.send(new AgentEventsMessage(List.of(event)))
            ));
            refreshViewport();
            return;
        }

        // 单测和 mock 事件源仍走同步路径，便于不启动真实 Program 也能验证完整 transcript。
        appendAgentEvents(eventSource.startTurn(normalizedTask, workspace, planMode));
        refreshViewport();
    }

    /**
     * 统一清洗用户任务文本；键盘提交和测试直调必须得到同一个空白判断结果。
     *
     * @param task 原始输入。
     * @return 去掉首尾空白后的任务文本。
     */
    private String normalizeTask(String task) {
        return task == null ? "" : task.trim();
    }

    /**
     * 判断当前是否已有一轮任务在后端流式执行；运行中不允许再次提交，保证 transcript 按问答轮次展开。
     *
     * @return true 表示当前轮尚未完成或出错。
     */
    private boolean isTurnRunning() {
        return "running".equals(status);
    }

    /**
     * 追加 Agent 事件并根据终态事件更新底部状态栏。
     *
     * @param events 后端或 mock 返回的事件。
     */
    private void appendAgentEvents(List<AgentEvent> events) {
        for (AgentEvent event : events) {
            if (event.eventType() == AgentEventType.ASSISTANT_DELTA) {
                appendAssistantDelta(event.payloadText("delta"));
                continue;
            }
            closeAssistantBlock();
            for (String line : transcriptRenderer.render(List.of(event))) {
                appendLine(line);
            }
        }
        if (events.stream().anyMatch(event -> event.eventType() == AgentEventType.ERROR)) {
            status = "error";
            return;
        }
        if (events.stream().anyMatch(event -> event.eventType() == AgentEventType.TURN_INTERRUPTED)) {
            status = "interrupted";
            return;
        }
        if (events.stream().anyMatch(event -> event.eventType() == AgentEventType.TURN_COMPLETED)) {
            status = "completed";
        }
    }

    /**
     * 合并 viewport 与 textarea 的后续命令，避免任一子组件的异步更新被覆盖。
     *
     * @param first 第一个子组件命令，可为空。
     * @param second 第二个子组件命令，可为空。
     * @return 合并后的命令，可为空。
     */
    private Command combine(Command first, Command second) {
        if (first != null && second != null) {
            return Command.batch(first, second);
        }
        if (first != null) {
            return first;
        }
        return second;
    }

    /**
     * 追加系统提示行；使用缩进而不是日志前缀，避免破坏对话流观感。
     *
     * @param line 系统提示内容。
     */
    private void appendSystemLine(String line) {
        appendLine("  " + line);
    }

    /**
     * 追加用户输入行，保持与截图风格一致的终端提示符。
     *
     * @param task 已清洗的用户任务文本。
     */
    private void appendUserLine(String task) {
        closeAssistantBlock();
        appendLine("> " + task);
    }

    /**
     * 将连续助手流片段合并到同一块回答中，避免 SSE 小片段在终端里变成散乱列表。
     *
     * @param delta 后端流式返回的助手文本片段。
     */
    private void appendAssistantDelta(String delta) {
        if (delta == null || delta.isBlank()) {
            return;
        }
        if (activeAssistantLineIndex < 0) {
            activeAssistantText = new StringBuilder();
            activeAssistantLineIndex = timelineLines.size();
            timelineLines.add("");
        }
        activeAssistantText.append(delta);
        timelineLines.set(activeAssistantLineIndex, formatAssistantLine(activeAssistantText.toString()));
        refreshViewport();
    }

    /**
     * 结束当前活动回答块；后续工具或新任务输出会从新的 transcript 行开始。
     */
    private void closeAssistantBlock() {
        activeAssistantLineIndex = -1;
        activeAssistantText = new StringBuilder();
    }

    /**
     * 给助手回答添加轻量说话者标签，比项目符号列表更接近真实对话流。
     *
     * @param text 助手回答正文。
     * @return transcript 可见行。
     */
    private String formatAssistantLine(String text) {
        return "CodingX  " + text;
    }

    /**
     * 追加 transcript 行并立即刷新 viewport，保证测试直接读取 view 时能看到最新状态。
     *
     * @param line 已渲染的 transcript 行。
     */
    private void appendLine(String line) {
        timelineLines.add(line);
        refreshViewport();
    }

    /**
     * 将内存中的 transcript 同步给滚动窗口，并把视口移动到底部显示最新任务结果。
     */
    private void refreshViewport() {
        viewport.setContent(String.join(System.lineSeparator(), timelineLines));
        viewport.gotoBottom();
    }

    /**
     * 渲染 TUI 当前视图。
     *
     * @return 可交给 tui4j 的终端界面文本。
     */
    @Override
    public String view() {
        List<String> sections = new ArrayList<>();
        if (!timelineLines.isEmpty()) {
            sections.add(renderTranscript());
        }
        sections.add(renderComposer());
        sections.add(statusBarRenderer.render(planMode, status, workspace));
        return String.join(GAP, sections);
    }

    /**
     * 渲染启动阶段的固定品牌区；该内容先写入普通 shell 输出，避免 tui4j 普通屏幕渲染区高度过小时裁掉 logo。
     *
     * @return 启动卡片和 Tip 文本。
     */
    public String startupBanner() {
        return headerRenderer.render(workspace)
            + GAP
            + "Tip: Build faster with CodingX.";
    }

    /**
     * 渲染 transcript；启动态不渲染 viewport，避免输入框被空白区域推到窗口底部。
     *
     * @return 可见 transcript 文本。
     */
    private String renderTranscript() {
        return String.join(System.lineSeparator(), timelineLines);
    }

    /**
     * 渲染紧凑输入行，不再给输入框增加额外边框。
     *
     * @return 可见 composer 文本。
     */
    private String renderComposer() {
        return textarea.view();
    }
}
