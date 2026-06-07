package com.codingx.cli.tui;

import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.AgentEventSource;
import com.codingx.cli.agent.AgentEventType;
import com.codingx.cli.agent.StreamingAgentEventSource;
import com.codingx.cli.auth.CliAuthService;
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
import org.jline.utils.WCWidth;

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
    private static final String GAP = "\n\n";

    /**
     * tui4j `RendererFlush` 固定按 LF 拆分逻辑行；Windows CRLF 会把 `\r` 留在行尾并破坏差量刷新。
     */
    private static final String RENDER_NEWLINE = "\n";

    /**
     * 空输入时展示的中文任务提示，避免旧英文模板被误认为系统残留任务。
     */
    private static final String COMPOSER_PLACEHOLDER = "输入任务，/ 查看命令";

    /**
     * 终端灰色前景色，用于把 placeholder 和真实用户输入区分开。
     */
    private static final String ANSI_DIM_PLACEHOLDER = "\u001B[90m";

    /**
     * ANSI 样式复位，防止灰色 placeholder 污染后续状态栏和回答内容。
     */
    private static final String ANSI_RESET = "\u001B[0m";

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
     * CLI 认证服务，用于在 TUI 输入框内消费 `/login` 和 `/logout` 控制命令。
     */
    private final CliAuthService cliAuthService;

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
     * 最近一次用户输入在 transcript 中的行号；长回答溢出终端时用于固定显示当前问题。
     */
    private int latestUserLineIndex;

    /**
     * 最近一次用户输入的可见行；当回答很长时放在回答窗口上方，避免被终端尾部裁剪掉。
     */
    private String latestUserLine;

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
     * 最近一次终端宽度，用于估算 transcript 可见区。
     */
    private int terminalWidth;

    /**
     * 最近一次终端高度；普通屏幕 renderer 会按该高度保留尾部内容。
     */
    private int terminalHeight;

    /**
     * 终端自动换行后的单个视觉行，保留来源 transcript 行号用于判断用户问题是否仍处于可见区。
     */
    private record VisualRow(int sourceLineIndex) {
    }

    /**
     * @param workspace 当前 CLI 工作区。
     * @param eventSource Agent 事件来源。
     * @param renderer 兼容旧构造链路的终端渲染器；截图风格 TUI 使用独立 transcript renderer。
     */
    public CodingXTuiModel(Path workspace, AgentEventSource eventSource, TerminalRenderer renderer) {
        this(workspace, eventSource, renderer, null);
    }

    /**
     * @param workspace 当前 CLI 工作区。
     * @param eventSource Agent 事件来源。
     * @param renderer 兼容旧构造链路的终端渲染器；截图风格 TUI 使用独立 transcript renderer。
     * @param cliAuthService CLI 认证服务；测试可为空，空时 `/login` 只给出命令行引导。
     */
    public CodingXTuiModel(
        Path workspace,
        AgentEventSource eventSource,
        TerminalRenderer renderer,
        CliAuthService cliAuthService
    ) {
        this.workspace = workspace;
        this.eventSource = eventSource;
        this.headerRenderer = new TuiHeaderRenderer();
        this.transcriptRenderer = new TuiTranscriptRenderer();
        this.statusBarRenderer = new TuiStatusBarRenderer();
        this.cliAuthService = cliAuthService;
        this.viewport = Viewport.create(80, 1);
        this.textarea = new Textarea();
        this.timelineLines = new ArrayList<>();
        this.latestUserLineIndex = -1;
        this.latestUserLine = "";
        this.streamExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "codingx-tui-stream");
            thread.setDaemon(true);
            return thread;
        });
        this.planMode = true;
        this.status = "ready";
        this.terminalWidth = 80;
        this.terminalHeight = Integer.MAX_VALUE;
        this.activeAssistantLineIndex = -1;
        this.activeAssistantText = new StringBuilder();

        configureTextarea();
    }

    /**
     * 配置底部输入框的基础体验；真实任务只能从这里提交，所以保持提示、宽度和焦点稳定。
     */
    private void configureTextarea() {
        textarea.setPlaceholder(COMPOSER_PLACEHOLDER);
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
                if (handleSlashCommand(normalizedTask)) {
                    textarea.reset();
                    return UpdateResult.from(this, null);
                }
                if (isTurnRunning()) {
                    // 当前轮还在流式输出时保留输入框内容，避免多条问题先堆出来、回答后到而破坏一问一答顺序。
                    return UpdateResult.from(this, null);
                }
                if (!ensureLoggedInBeforeChat()) {
                    textarea.reset();
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
        terminalWidth = safeWidth;
        terminalHeight = Math.max(height, 8);
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
     * 处理 TUI 内部控制命令；这些命令不应发送到后端聊天流，否则未登录场景会变成普通请求失败。
     *
     * @param task 已清洗的输入内容。
     * @return true 表示该输入已被本地命令消费。
     */
    private boolean handleSlashCommand(String task) {
        String normalizedCommand = task.trim().toLowerCase();
        if (!normalizedCommand.startsWith("/")) {
            return false;
        }
        closeAssistantBlock();
        latestUserLine = "> " + task;
        latestUserLineIndex = timelineLines.size();
        appendLine(latestUserLine);
        switch (normalizedCommand) {
            case "/login" -> runLoginCommand();
            case "/logout" -> runLogoutCommand();
            case "/help", "/" -> {
                appendCommandHelp();
                status = "ready";
            }
            default -> {
                appendSystemLine("未知命令：" + task);
                appendCommandHelp();
                status = "error";
            }
        }
        refreshViewport();
        return true;
    }

    /**
     * 执行浏览器登录命令；认证服务缺失时给出命令行兜底，避免测试和旧嵌入方空指针。
     */
    private void runLoginCommand() {
        status = "running";
        if (cliAuthService == null) {
            appendSystemLine("请运行 codingx auth login 打开浏览器登录。");
            status = "error";
            return;
        }
        boolean success = cliAuthService.loginWithBrowser();
        appendSystemLine(success ? "CLI 登录成功" : "CLI 登录失败，请尝试 codingx auth login --device。");
        status = success ? "completed" : "error";
    }

    /**
     * 执行退出登录命令；只清理本机 token，不影响后端账号和其他浏览器会话。
     */
    private void runLogoutCommand() {
        status = "running";
        if (cliAuthService == null) {
            appendSystemLine("请运行 codingx logout 清理本机登录态。");
            status = "error";
            return;
        }
        boolean success = cliAuthService.logout();
        appendSystemLine(success ? "CLI 已退出登录" : "CLI 退出登录失败");
        status = success ? "completed" : "error";
    }

    /**
     * 展示 CLI 内部命令列表；命令面板是纯文本区域，保持普通终端可读且不引入新 TUI 组件复杂度。
     */
    private void appendCommandHelp() {
        appendSystemLine("可用命令");
        appendSystemLine("/login   登录 CodingX");
        appendSystemLine("/logout  退出登录");
        appendSystemLine("/help    查看命令列表");
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
     * 普通聊天提交前确保 CLI 已登录；无 token 时直接打开浏览器登录，成功后继续原任务。
     *
     * @return true 表示可以继续提交到后端聊天流。
     */
    private boolean ensureLoggedInBeforeChat() {
        if (cliAuthService == null || cliAuthService.isLoggedIn()) {
            return true;
        }
        status = "running";
        appendSystemLine("未登录或登录已失效，正在打开浏览器登录 CodingX。");
        boolean success = cliAuthService.loginWithBrowser();
        if (success) {
            appendSystemLine("CLI 登录成功，继续发送当前请求。");
            status = "ready";
            return true;
        }
        appendSystemLine("CLI 登录失败，请运行 /login 或 codingx auth login --device 后重试。");
        status = "error";
        refreshViewport();
        return false;
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
        latestUserLine = "> " + task;
        latestUserLineIndex = timelineLines.size();
        appendLine(latestUserLine);
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
        viewport.setContent(String.join(RENDER_NEWLINE, timelineLines));
        viewport.gotoBottom();
    }

    /**
     * 渲染 TUI 当前视图。
     *
     * @return 可交给 tui4j 的终端界面文本。
     */
    @Override
    public String view() {
        String inputAndStatus = renderInputBlock() + RENDER_NEWLINE + renderStatusBar();
        List<String> sections = new ArrayList<>();
        if (!timelineLines.isEmpty()) {
            sections.add(renderTranscript());
        }
        if (shouldRenderSlashCommandPanel()) {
            sections.add(renderSlashCommandPanel());
        }
        // 用户消息、输入框和状态栏必须连续占用底部三行；tui4j 普通 renderer 只保留尾部可见行时不能让空行挤掉问题。
        sections.add(inputAndStatus);
        return wrapRendererView(String.join(GAP, sections));
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
        String transcript = String.join(RENDER_NEWLINE, timelineLines);
        if (!shouldPinLatestUserLine()) {
            return transcript;
        }

        List<String> currentTurnLines = timelineLines.subList(
            Math.min(latestUserLineIndex + 1, timelineLines.size()),
            timelineLines.size()
        );
        int userLineHeight = renderedVisualLineCount(latestUserLine);
        int answerHeight = Math.max(availableTranscriptLines() - userLineHeight, 1);
        return latestUserLine + RENDER_NEWLINE + renderVisualTail(currentTurnLines, answerHeight);
    }

    /**
     * 渲染紧凑输入行，不再给输入框增加额外边框。
     *
     * @return 可见 composer 文本。
     */
    private String renderComposer() {
        String taskText = normalizeRendererNewlines(textarea.value()).replace('\n', ' ').trim();
        // 真实 TTY 下 tui4j Textarea.view() 会附带光标样式和填充行；底部 composer 必须稳定为单行。
        if (taskText.isEmpty()) {
            return stylePlaceholder(truncateVisualLine("› " + COMPOSER_PLACEHOLDER));
        }
        return truncateVisualLine("› " + taskText);
    }

    /**
     * 将空输入提示渲染为灰色文本，使它看起来像 placeholder 而不是一条真实消息。
     *
     * @param placeholder 已按终端宽度截断的可见提示行。
     * @return 带 ANSI 灰色样式的提示行。
     */
    private String stylePlaceholder(String placeholder) {
        return ANSI_DIM_PLACEHOLDER + placeholder + ANSI_RESET;
    }

    /**
     * 判断是否应在输入区上方展示 Slash Command 面板；只要当前输入以 `/` 开头就给出候选。
     *
     * @return true 表示展示命令列表。
     */
    private boolean shouldRenderSlashCommandPanel() {
        return normalizeRendererNewlines(textarea.value()).trim().startsWith("/");
    }

    /**
     * 渲染输入中的命令候选；保持纯文本而非复杂交互控件，适配普通终端和测试输出。
     *
     * @return 命令候选文本。
     */
    private String renderSlashCommandPanel() {
        String keyword = normalizeRendererNewlines(textarea.value()).trim()
            .replaceFirst("^/+", "")
            .toLowerCase();
        List<String> commands = List.of(
            "/login   登录 CodingX",
            "/logout  退出登录",
            "/help    查看命令列表"
        ).stream()
            .filter(line -> keyword.isBlank() || line.toLowerCase().contains(keyword))
            .toList();
        if (commands.isEmpty()) {
            return "  未匹配到命令";
        }
        List<String> lines = new ArrayList<>();
        lines.add("  命令");
        for (String command : commands) {
            lines.add("  " + command);
        }
        return String.join(RENDER_NEWLINE, lines);
    }

    /**
     * 渲染底部输入块；普通场景只保留 composer，避免用户问题同时出现在 transcript 和输入框上方。
     *
     * @return 当前 composer。
     */
    private String renderInputBlock() {
        return renderComposer();
    }

    /**
     * 渲染单行状态栏；状态栏是底部控件，不应该像后端正文一样自动换行挤占用户问题和输入框。
     *
     * @return 当前终端宽度下的一行状态栏。
     */
    private String renderStatusBar() {
        String fullStatusBar = statusBarRenderer.render(planMode, status, workspace);
        if (renderedVisualLineCount(fullStatusBar) <= 1) {
            return fullStatusBar;
        }

        String planLabel = planMode ? "Plan mode" : "Chat mode";
        String compactStatusBar = "server selected · " + status + " · " + planLabel;
        if (renderedVisualLineCount(compactStatusBar) <= 1) {
            return compactStatusBar;
        }

        return truncateVisualLine(status + " · " + planLabel);
    }

    /**
     * 判断是否需要在输入框上方固定展示当前问题。
     * 当前问题固定由 transcript 溢出兜底处理，输入框上方不再常驻显示，避免短回答重复渲染用户消息。
     *
     * @return false 表示 composer 区不额外渲染用户问题。
     */
    private boolean shouldRenderCurrentQuestionAnchor() {
        return false;
    }

    /**
     * 渲染当前问题固定区；保留和 transcript 一致的 `>` 提示符，方便用户确认本轮正在回答哪条消息。
     *
     * @return 当前用户问题。
     */
    private String renderCurrentQuestionAnchor() {
        return latestUserLine;
    }

    /**
     * tui4j 标准 renderer 只按 `\n` 统计行数，但 Windows Terminal 会对超长逻辑行自动换行。
     * 如果直接输出后端 JSON、长 Markdown 或长路径，renderer 的光标回退范围会小于真实视觉行数，后续刷新就可能覆盖用户问题。
     *
     * @param view 已按业务分区拼好的完整视图。
     * @return 按当前终端宽度预换行后的视图，保证 renderer 看到的逻辑行等于终端视觉行。
     */
    private String wrapRendererView(String view) {
        List<String> rows = new ArrayList<>();
        for (String logicalLine : view.split("\\R", -1)) {
            if (logicalLine.isEmpty()) {
                rows.add("");
                continue;
            }
            rows.addAll(wrapVisualLines(logicalLine));
        }
        return String.join(RENDER_NEWLINE, rows);
    }

    /**
     * 判断是否需要把最近用户问题固定到可见区；只有完整视图会被普通屏幕裁剪时才启用，短回答不重复显示问题。
     *
     * @return true 表示需要固定最近用户问题。
     */
    private boolean shouldPinLatestUserLine() {
        if (latestUserLineIndex < 0 || latestUserLine.isBlank()) {
            return false;
        }
        return lastVisibleRows(renderFullViewRows(), terminalHeight).stream()
            .noneMatch(row -> row.sourceLineIndex() == latestUserLineIndex);
    }

    /**
     * 计算 transcript 在当前终端中最多可占用的行数，预留输入框、状态栏以及 section 间空行。
     *
     * @return transcript 可见行数，至少为 1。
     */
    private int availableTranscriptLines() {
        return Math.max(terminalHeight - nonTranscriptLineCount(), 1);
    }

    /**
     * 计算输入框、状态栏和 section 间隔占用的行数；与 view() 的分区拼接方式保持一致。
     *
     * @return 非 transcript 区域占用行数。
     */
    private int nonTranscriptLineCount() {
        int currentQuestionAnchorLines = shouldRenderCurrentQuestionAnchor()
            ? renderedVisualLineCount(renderCurrentQuestionAnchor())
            : 0;
        int sectionGapLines = timelineLines.isEmpty() ? 0 : 1;
        return currentQuestionAnchorLines
            + renderedVisualLineCount(renderComposer())
            + renderedVisualLineCount(renderStatusBar())
            + sectionGapLines;
    }

    /**
     * 组装完整视图的视觉行，用于判断普通屏幕尾部裁剪后是否还能看到用户问题；不直接返回给 tui4j。
     *
     * @return transcript、输入框和状态栏对应的视觉行。
     */
    private List<VisualRow> renderFullViewRows() {
        List<VisualRow> rows = new ArrayList<>();
        for (int index = 0; index < timelineLines.size(); index++) {
            appendVisualRows(rows, timelineLines.get(index), index);
        }
        if (!timelineLines.isEmpty()) {
            appendGapRow(rows);
        }
        if (shouldRenderCurrentQuestionAnchor()) {
            appendVisualRows(rows, renderCurrentQuestionAnchor(), latestUserLineIndex);
        }
        appendVisualRows(rows, renderComposer(), -1);
        appendVisualRows(rows, renderStatusBar(), -1);
        return rows;
    }

    /**
     * 渲染当前轮回答的视觉行尾部，保证长回答保留最新输出，同时让当前问题作为固定行留在上方。
     *
     * @param lines 当前轮回答与状态行。
     * @param maxLines 最多展示行数。
     * @return 尾部可见内容。
     */
    private String renderVisualTail(List<String> lines, int maxLines) {
        List<String> visualLines = new ArrayList<>();
        for (String line : lines) {
            visualLines.addAll(wrapVisualLines(line));
        }
        if (visualLines.isEmpty()) {
            return "";
        }
        int fromIndex = Math.max(visualLines.size() - maxLines, 0);
        return String.join(RENDER_NEWLINE, visualLines.subList(fromIndex, visualLines.size()));
    }

    /**
     * 按普通屏幕 renderer 的尾部保留策略获取可见视觉行，判断用户问题是否已被长回答顶出屏幕。
     *
     * @param rows 完整渲染视觉行。
     * @param maxLines 终端可见行数。
     * @return 尾部可见视觉行。
     */
    private List<VisualRow> lastVisibleRows(List<VisualRow> rows, int maxLines) {
        if (rows.isEmpty()) {
            return List.of();
        }
        int safeMaxLines = Math.max(maxLines, 1);
        int fromIndex = Math.max(rows.size() - safeMaxLines, 0);
        return rows.subList(fromIndex, rows.size());
    }

    /**
     * 追加一个分区间隔视觉行；与 view() 使用的双换行分区符保持一致。
     *
     * @param rows 完整视图视觉行集合。
     */
    private void appendGapRow(List<VisualRow> rows) {
        rows.add(new VisualRow(-1));
    }

    /**
     * 按终端宽度追加文本视觉行，并保留 transcript 来源行号。
     *
     * @param value 渲染文本。
     * @param sourceLineIndex 来源 transcript 行号，非 transcript 区域使用 -1。
     */
    private void appendVisualRows(List<VisualRow> rows, String value, int sourceLineIndex) {
        for (String visualLine : wrapVisualLines(value)) {
            rows.add(new VisualRow(sourceLineIndex));
        }
    }

    /**
     * 统计文本在当前终端宽度下的视觉行数；空字符串按 0 行处理，避免初始态错误触发溢出逻辑。
     *
     * @param value 渲染文本。
     * @return 自动换行后的视觉行数。
     */
    private int renderedVisualLineCount(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        return wrapVisualLines(value).size();
    }

    /**
     * 按终端宽度把逻辑文本拆成视觉行；中英文字符宽度使用 JLine 的 WCWidth，避免长中文或 Markdown 行误判可见区。
     *
     * @param value 可能包含换行的渲染文本。
     * @return 自动换行后的视觉行列表。
     */
    private List<String> wrapVisualLines(String value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        List<String> rows = new ArrayList<>();
        for (String logicalLine : normalizeRendererNewlines(value).lines().toList()) {
            appendWrappedLogicalLine(rows, logicalLine);
        }
        return rows;
    }

    /**
     * 规范化为 tui4j renderer 可正确识别的 LF；不能把 Windows CR 留给 `RendererFlush.split("\\n")`。
     *
     * @param value 原始渲染文本。
     * @return 仅包含 LF 的渲染文本。
     */
    private String normalizeRendererNewlines(String value) {
        return value.replace("\r\n", RENDER_NEWLINE).replace("\r", RENDER_NEWLINE);
    }

    /**
     * 将单行文本截断到当前终端宽度；用于状态栏这类控件，避免它变成多行正文。
     *
     * @param value 单行文本。
     * @return 不超过当前终端宽度的文本。
     */
    private String truncateVisualLine(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int maxColumns = Math.max(terminalWidth, 1);
        StringBuilder row = new StringBuilder();
        int columns = 0;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            int width = codePointWidth(codePoint);
            if (columns + width > maxColumns && !row.isEmpty()) {
                break;
            }
            row.appendCodePoint(codePoint);
            columns += width;
            offset += Character.charCount(codePoint);
        }
        return row.toString();
    }

    /**
     * 拆分单个逻辑行；当下一字符会超过终端宽度时先结束当前视觉行。
     *
     * @param rows 输出视觉行集合。
     * @param logicalLine 当前逻辑行。
     */
    private void appendWrappedLogicalLine(List<String> rows, String logicalLine) {
        StringBuilder row = new StringBuilder();
        int columns = 0;
        for (int offset = 0; offset < logicalLine.length(); ) {
            int codePoint = logicalLine.codePointAt(offset);
            int width = codePointWidth(codePoint);
            if (columns + width > terminalWidth && !row.isEmpty()) {
                rows.add(row.toString());
                row = new StringBuilder();
                columns = 0;
            }
            row.appendCodePoint(codePoint);
            columns += width;
            offset += Character.charCount(codePoint);
        }
        rows.add(row.toString());
    }

    /**
     * 计算单个 Unicode code point 在终端中的显示宽度；控制字符和不可显示字符不占宽。
     *
     * @param codePoint 当前字符。
     * @return 终端列宽，最小为 0。
     */
    private int codePointWidth(int codePoint) {
        return Math.max(WCWidth.wcwidth(codePoint), 0);
    }
}
