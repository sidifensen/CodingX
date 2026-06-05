package com.codingx.cli.tui;

import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.AgentEventSource;
import com.codingx.cli.agent.AgentEventType;
import com.codingx.cli.render.TerminalRenderer;
import com.williamcallahan.tui4j.compat.bubbletea.Command;
import com.williamcallahan.tui4j.compat.bubbletea.KeyPressMessage;
import com.williamcallahan.tui4j.compat.bubbletea.Message;
import com.williamcallahan.tui4j.compat.bubbletea.Model;
import com.williamcallahan.tui4j.compat.bubbletea.QuitMessage;
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult;
import com.williamcallahan.tui4j.compat.bubbletea.WindowSizeMessage;
import com.williamcallahan.tui4j.compat.bubbles.cursor.Cursor;
import com.williamcallahan.tui4j.compat.bubbles.textarea.Textarea;
import com.williamcallahan.tui4j.compat.bubbles.viewport.Viewport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CodingX TUI 的 Bubble Tea 模型，维护事件窗口、任务输入框和运行状态。
 */
public class CodingXTuiModel implements Model {

    /**
     * 事件区域和输入框之间的空行间隔。
     */
    private static final String GAP = System.lineSeparator() + System.lineSeparator();

    /**
     * 当前 CLI 工作区，后续真实 Agent API 会把它作为 session workspace。
     */
    private final Path workspace;

    /**
     * Agent 事件来源；当前使用 mock，后续替换为后端事件流客户端。
     */
    private final AgentEventSource eventSource;

    /**
     * 事件到终端文本的渲染器，保证 TUI 与未来普通日志输出复用同一语义。
     */
    private final TerminalRenderer renderer;

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
     * 当前运行状态，展示在底部状态栏。
     */
    private String status;

    /**
     * @param workspace 当前 CLI 工作区。
     * @param eventSource Agent 事件来源。
     * @param renderer 终端事件渲染器。
     */
    public CodingXTuiModel(Path workspace, AgentEventSource eventSource, TerminalRenderer renderer) {
        this.workspace = workspace;
        this.eventSource = eventSource;
        this.renderer = renderer;
        this.viewport = Viewport.create(80, 18);
        this.textarea = new Textarea();
        this.timelineLines = new ArrayList<>();
        this.status = "ready";

        configureTextarea();
        appendSystemLine("CodingX TUI 已启动。输入任务后按 Enter 提交，按 Esc 或 Ctrl+C 退出。");
    }

    private void configureTextarea() {
        textarea.setPlaceholder("输入任务后按 Enter");
        textarea.setPrompt("> ");
        textarea.setCharLimit(1000);
        textarea.setWidth(80);
        textarea.setHeight(3);
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
     * 处理按键、窗口尺寸和子组件事件。
     *
     * @param msg tui4j 消息。
     * @return 更新后的模型和后续命令。
     */
    @Override
    public UpdateResult<? extends Model> update(Message msg) {
        if (msg instanceof WindowSizeMessage windowSizeMessage) {
            resize(windowSizeMessage.width(), windowSizeMessage.height());
        }

        UpdateResult<? extends Model> viewportResult = viewport.update(msg);
        UpdateResult<? extends Model> textareaResult = textarea.update(msg);
        Command command = combine(viewportResult.command(), textareaResult.command());

        if (msg instanceof KeyPressMessage keyPressMessage) {
            String key = keyPressMessage.key();
            if ("ctrl+c".equals(key) || "esc".equals(key)) {
                return UpdateResult.from(this, QuitMessage::new);
            }
            if ("enter".equals(key)) {
                submitTask(textarea.value());
                textarea.reset();
            }
        }

        return UpdateResult.from(this, command);
    }

    private void resize(int width, int height) {
        int safeWidth = Math.max(width, 40);
        int viewportHeight = Math.max(height - 8, 8);
        viewport.setWidth(safeWidth);
        viewport.setHeight(viewportHeight);
        textarea.setWidth(safeWidth);
        textarea.setHeight(3);
        refreshViewport();
    }

    /**
     * 提交任务并把返回事件追加到 TUI 时间线；测试直接调用该方法避免启动真实终端。
     *
     * @param task 用户任务。
     */
    public void submitTask(String task) {
        String normalizedTask = task == null ? "" : task.trim();
        if (normalizedTask.isEmpty()) {
            return;
        }

        status = "running";
        appendUserLine(normalizedTask);
        List<AgentEvent> events = eventSource.startTurn(normalizedTask, workspace);
        for (String line : renderer.render(events)) {
            appendLine(line);
        }
        status = events.stream().anyMatch(event -> event.eventType() == AgentEventType.ERROR) ? "error" : "completed";
        refreshViewport();
    }

    private Command combine(Command first, Command second) {
        if (first != null && second != null) {
            return Command.batch(first, second);
        }
        if (first != null) {
            return first;
        }
        return second;
    }

    private void appendSystemLine(String line) {
        appendLine("[system] " + line);
    }

    private void appendUserLine(String task) {
        appendLine("> " + task);
    }

    private void appendLine(String line) {
        timelineLines.add(line);
        refreshViewport();
    }

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
        return header() + GAP
            + viewport.view() + GAP
            + textarea.view() + GAP
            + statusBar();
    }

    private String header() {
        return "CodingX TUI" + System.lineSeparator()
            + "Workspace: " + workspace + System.lineSeparator()
            + "输入任务后按 Enter，Esc/Ctrl+C 退出";
    }

    private String statusBar() {
        return "状态: " + status;
    }
}
