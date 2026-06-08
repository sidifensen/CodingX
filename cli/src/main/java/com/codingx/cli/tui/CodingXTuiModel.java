package com.codingx.cli.tui;

import com.codingx.cli.agent.AgentEvent;
import com.codingx.cli.agent.AgentEventSource;
import com.codingx.cli.agent.AgentEventType;
import com.codingx.cli.agent.StreamingAgentEventSource;
import com.codingx.cli.auth.CliAuthService;
import com.codingx.cli.render.TerminalRenderer;
import com.codingx.cli.slash.CliSlashCommand;
import com.codingx.cli.slash.SlashCommandCatalog;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
     * 底部输入框的块状光标，解决普通 Windows Terminal 下输入位置不可见的问题。
     */
    private static final String VISIBLE_CURSOR = "█";

    /**
     * 运行中等待提示的刷新周期；只负责重绘秒数，不改变后端请求超时。
     */
    private static final Duration RUNNING_TICK_INTERVAL = Duration.ofSeconds(1);

    /**
     * 纳秒到秒的换算常量，保证等待计时不受系统时区影响。
     */
    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

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
     * 后端治理中心 Slash Command 目录，用于让 CLI 面板展示管理端启用命令。
     */
    private final SlashCommandCatalog slashCommandCatalog;

    /**
     * 已加载的后端 Slash Command 快照；目录失败时为空，但本地控制命令仍可用。
     */
    private final List<CliSlashCommand> backendSlashCommands;

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
     * 当前正在消费的流式任务；Esc 中断时通过 Future 发送 interrupt。
     */
    private Future<?> activeStreamTask;

    /**
     * 当前正在等待的浏览器登录任务；登录必须后台执行，避免 loopback 回调等待阻塞键盘事件。
     */
    private Future<?> activeAuthTask;

    /**
     * 当前流式轮次序号；中断后递增，后台迟到事件不会再写回 TUI。
     */
    private volatile long streamTurnSerial;

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
    private volatile String status;

    /**
     * 当前轮开始运行的纳秒时间，用于渲染 `Working (Ns • esc to interrupt)`。
     */
    private long runningTurnStartedAtNanos;

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
     * 运行中计时消息，只触发重绘和下一次计时。
     */
    private record RunningTickMessage() implements Message {
    }

    /**
     * 后台浏览器登录完成后回到 TUI 主循环的消息；pendingTask 非空时登录成功后继续发送原任务。
     */
    private record BrowserLoginResultMessage(boolean success, String pendingTask) implements Message {
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
        this(workspace, eventSource, renderer, cliAuthService, SlashCommandCatalog.EMPTY);
    }

    /**
     * @param workspace 当前 CLI 工作区。
     * @param eventSource Agent 事件来源。
     * @param renderer 兼容旧构造链路的终端渲染器；截图风格 TUI 使用独立 transcript renderer。
     * @param cliAuthService CLI 认证服务；测试可为空，空时 `/login` 只给出命令行引导。
     * @param slashCommandCatalog 后端 Slash Command 目录；为空时只展示本地控制命令。
     */
    public CodingXTuiModel(
        Path workspace,
        AgentEventSource eventSource,
        TerminalRenderer renderer,
        CliAuthService cliAuthService,
        SlashCommandCatalog slashCommandCatalog
    ) {
        this.workspace = workspace;
        this.eventSource = eventSource;
        this.headerRenderer = new TuiHeaderRenderer();
        this.transcriptRenderer = new TuiTranscriptRenderer();
        this.statusBarRenderer = new TuiStatusBarRenderer();
        this.cliAuthService = cliAuthService;
        this.slashCommandCatalog = slashCommandCatalog == null ? SlashCommandCatalog.EMPTY : slashCommandCatalog;
        this.backendSlashCommands = loadBackendSlashCommands(this.slashCommandCatalog);
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
        this.activeStreamTask = null;
        this.activeAuthTask = null;
        this.streamTurnSerial = 0L;
        this.planMode = true;
        this.status = "ready";
        this.runningTurnStartedAtNanos = 0L;
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
        // 自定义单行 composer 不直接调用 Textarea.view()，这里保留 cursor 状态让 blink 消息驱动块状光标闪烁。
        textarea.cursor().setBlink(true);
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

        if (msg instanceof RunningTickMessage) {
            return UpdateResult.from(this, isTurnRunning() ? runningTickCommand() : null);
        }

        if (msg instanceof BrowserLoginResultMessage browserLoginResultMessage) {
            return UpdateResult.from(this, handleBrowserLoginResult(browserLoginResultMessage));
        }

        if (msg instanceof WindowSizeMessage windowSizeMessage) {
            resize(windowSizeMessage.width(), windowSizeMessage.height());
        }

        if (msg instanceof KeyPressMessage keyPressMessage) {
            String key = keyPressMessage.key();
            if ("ctrl+c".equals(key)) {
                shutdownBackgroundTasks();
                return UpdateResult.from(this, QuitMessage::new);
            }
            if ("esc".equals(key) || keyPressMessage.type() == KeyType.keyESC) {
                if (isTurnRunning()) {
                    interruptRunningTurn();
                    return UpdateResult.from(this, null);
                }
                shutdownBackgroundTasks();
                return UpdateResult.from(this, QuitMessage::new);
            }
            if ("shift+tab".equals(key) || keyPressMessage.type() == KeyType.KeyShiftTab) {
                planMode = !planMode;
                return UpdateResult.from(this, null);
            }
            if (isTabKey(keyPressMessage) && completeUniqueSlashCommand()) {
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
                Command submitCommand = submitTaskAfterLogin(normalizedTask);
                textarea.reset();
                return UpdateResult.from(this, submitCommand);
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
     * 判断当前按键是否为 Tab；tui4j 把普通 Tab 映射为水平制表符 HT。
     *
     * @param keyPressMessage 键盘事件。
     * @return true 表示应尝试补全 Slash Command。
     */
    private boolean isTabKey(KeyPressMessage keyPressMessage) {
        return "tab".equals(keyPressMessage.key()) || keyPressMessage.type() == KeyType.keyHT;
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
        submitTaskAndReturnCommand(task);
    }

    /**
     * 提交任务并返回需要交给 Program 执行的后续命令；流式请求会启动等待计时刷新。
     *
     * @param task 用户任务。
     * @return 运行中 tick 命令；同步事件源无后续命令。
     */
    private Command submitTaskAndReturnCommand(String task) {
        String normalizedTask = normalizeTask(task);
        if (normalizedTask.isEmpty()) {
            return null;
        }

        status = "running";
        runningTurnStartedAtNanos = System.nanoTime();
        appendUserLine(normalizedTask);
        if (eventSource instanceof StreamingAgentEventSource streamingEventSource && program != null) {
            // 真实后端 SSE 在后台线程消费；每个事件通过 Program.send 回到 update()，避免跨线程直接改 UI 状态。
            long currentTurnSerial = ++streamTurnSerial;
            activeStreamTask = streamExecutor.submit(() -> streamingEventSource.startTurn(
                normalizedTask,
                workspace,
                planMode,
                event -> {
                    // Esc 中断后可能仍有迟到 SSE 事件，这里按轮次过滤，避免旧事件写回新状态。
                    if (isCurrentStreamTurn(currentTurnSerial)) {
                        program.send(new AgentEventsMessage(List.of(event)));
                    }
                }
            ));
            refreshViewport();
            return runningTickCommand();
        }

        // 单测和 mock 事件源仍走同步路径，便于不启动真实 Program 也能验证完整 transcript。
        appendAgentEvents(eventSource.startTurn(normalizedTask, workspace, planMode));
        refreshViewport();
        return null;
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
        if (isBackendSlashCommand(normalizedCommand)) {
            // 后端内置命令必须走聊天流结构化 messages 参数，不能被本地控制命令分支吞掉。
            return false;
        }
        closeAssistantBlock();
        latestUserLine = "› " + task;
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
        if (cliAuthService == null) {
            appendSystemLine("请运行 codingx auth login 打开浏览器登录。");
            status = "error";
            return;
        }
        startBrowserLogin(null);
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
        for (CliSlashCommand command : backendSlashCommands) {
            appendSystemLine(formatBackendSlashCommand(command));
        }
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
     * 普通聊天提交前检查 CLI 登录态；真实 TUI 中自动登录会后台执行，成功后继续提交原任务。
     *
     * @param task 用户原始任务。
     * @return 可立即执行的后续命令；后台登录场景返回空。
     */
    private Command submitTaskAfterLogin(String task) {
        if (cliAuthService == null || cliAuthService.isLoggedIn()) {
            return submitTaskAndReturnCommand(task);
        }
        if (program != null) {
            startBrowserLogin(task);
            return null;
        }
        return submitTaskAfterSynchronousLogin(task);
    }

    /**
     * 无真实 Program 的测试或嵌入链路继续同步登录，避免调用方额外处理异步消息。
     *
     * @param task 登录成功后要继续发送的任务。
     * @return 登录成功后的提交命令；失败时为空。
     */
    private Command submitTaskAfterSynchronousLogin(String task) {
        status = "running";
        appendSystemLine("未登录或登录已失效，正在打开浏览器登录 CodingX。");
        boolean success = cliAuthService.loginWithBrowser();
        if (success) {
            appendSystemLine("CLI 登录成功，继续发送当前请求。");
            status = "ready";
            return submitTaskAndReturnCommand(task);
        }
        appendSystemLine("CLI 登录失败，请运行 /login 或 codingx auth login --device 后重试。");
        status = "error";
        refreshViewport();
        return null;
    }

    /**
     * 后台执行浏览器登录，避免浏览器授权等待期间 TUI 无法响应 Esc/Ctrl+C。
     *
     * @param pendingTask 登录成功后要继续发送的用户任务；为空表示用户只执行了 `/login`。
     */
    private void startBrowserLogin(String pendingTask) {
        closeAssistantBlock();
        status = "authenticating";
        appendSystemLine(pendingTask == null
            ? "正在打开浏览器登录 CodingX，授权完成后会自动返回；Esc 可退出。"
            : "未登录或登录已失效，正在打开浏览器登录 CodingX；授权完成后继续发送当前请求。");
        if (program == null) {
            handleBrowserLoginResult(new BrowserLoginResultMessage(cliAuthService.loginWithBrowser(), pendingTask));
            return;
        }
        activeAuthTask = streamExecutor.submit(() -> {
            boolean success = cliAuthService.loginWithBrowser();
            if (!Thread.currentThread().isInterrupted() && program != null) {
                program.send(new BrowserLoginResultMessage(success, pendingTask));
            }
        });
        refreshViewport();
    }

    /**
     * 处理后台浏览器登录结果；自动登录成功时继续发送原任务。
     *
     * @param message 登录结果消息。
     * @return 后续提交命令，可为空。
     */
    private Command handleBrowserLoginResult(BrowserLoginResultMessage message) {
        activeAuthTask = null;
        if (message.success()) {
            if (message.pendingTask() == null || message.pendingTask().isBlank()) {
                appendSystemLine("CLI 登录成功");
                status = "completed";
                refreshViewport();
                return null;
            }
            appendSystemLine("CLI 登录成功，继续发送当前请求。");
            status = "ready";
            return submitTaskAndReturnCommand(message.pendingTask());
        }
        appendSystemLine(message.pendingTask() == null || message.pendingTask().isBlank()
            ? "CLI 登录失败，请尝试 codingx auth login --device。"
            : "CLI 登录失败，请运行 /login 或 codingx auth login --device 后重试。");
        status = "error";
        refreshViewport();
        return null;
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
            activeStreamTask = null;
            status = "error";
            return;
        }
        if (events.stream().anyMatch(event -> event.eventType() == AgentEventType.TURN_INTERRUPTED)) {
            activeStreamTask = null;
            status = "interrupted";
            return;
        }
        if (events.stream().anyMatch(event -> event.eventType() == AgentEventType.TURN_COMPLETED)) {
            activeStreamTask = null;
            status = "completed";
        }
    }

    /**
     * 判断后台 SSE 事件是否仍属于当前运行轮次；Esc 中断会递增轮次号，让迟到回调自然丢弃。
     *
     * @param turnSerial 后台线程捕获的轮次号。
     * @return true 表示事件仍可写回 TUI。
     */
    private boolean isCurrentStreamTurn(long turnSerial) {
        return isTurnRunning() && streamTurnSerial == turnSerial && program != null;
    }

    /**
     * 中断当前流式回答；只停止本轮 AI 回复，不退出整个 TUI 程序。
     */
    private void interruptRunningTurn() {
        closeAssistantBlock();
        streamTurnSerial++;
        if (activeStreamTask != null) {
            activeStreamTask.cancel(true);
            activeStreamTask = null;
        }
        runningTurnStartedAtNanos = 0L;
        status = "interrupted";
        appendLine("• Interrupted");
        refreshViewport();
    }

    /**
     * 退出 TUI 前停止所有后台任务；包括 SSE 流和浏览器登录等待，避免退出后仍占用回调端口。
     */
    private void shutdownBackgroundTasks() {
        if (activeAuthTask != null) {
            activeAuthTask.cancel(true);
            activeAuthTask = null;
        }
        if (activeStreamTask != null) {
            activeStreamTask.cancel(true);
            activeStreamTask = null;
        }
        streamExecutor.shutdownNow();
    }

    /**
     * 构造运行中计时命令；tick 回到 update 后会继续排下一次，直到本轮结束。
     *
     * @return tui4j tick 命令。
     */
    private Command runningTickCommand() {
        return Command.tick(RUNNING_TICK_INTERVAL, ignored -> new RunningTickMessage());
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
        latestUserLine = "› " + task;
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
            if (!timelineLines.isEmpty() && latestUserLine.equals(timelineLines.getLast())) {
                appendLine("");
            }
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
        return "• " + text;
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
        viewport.setContent(String.join(RENDER_NEWLINE, renderTranscriptLines()));
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
        return wrapRendererView(String.join(RENDER_NEWLINE, sections));
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
        List<String> transcriptLines = renderTranscriptLines();
        String transcript = String.join(RENDER_NEWLINE, transcriptLines);
        if (!shouldPinLatestUserLine()) {
            return transcript;
        }

        List<String> currentTurnLines = transcriptLines.subList(
            Math.min(latestUserLineIndex + 1, transcriptLines.size()),
            transcriptLines.size()
        );
        int userLineHeight = renderedVisualLineCount(latestUserLine);
        int answerHeight = Math.max(availableTranscriptLines() - userLineHeight, 1);
        return latestUserLine + RENDER_NEWLINE + renderVisualTail(currentTurnLines, answerHeight);
    }

    /**
     * 返回当前 transcript 派生行；运行中等待提示只参与渲染，不写入历史消息，避免完成后残留。
     *
     * @return 实际历史行加运行态临时提示。
     */
    private List<String> renderTranscriptLines() {
        List<String> lines = new ArrayList<>(timelineLines);
        if (isTurnRunning()) {
            lines.add(renderWorkingLine());
        }
        return lines;
    }

    /**
     * 渲染当前轮等待提示，向用户说明正在生成并可按 Esc 中断。
     *
     * @return 运行中提示行。
     */
    private String renderWorkingLine() {
        return styleDim("• Working (" + elapsedRunningSeconds() + "s • esc to interrupt)");
    }

    /**
     * 计算当前轮已等待秒数，使用单调时钟避免系统时间调整影响计时。
     *
     * @return 当前轮已运行秒数。
     */
    private long elapsedRunningSeconds() {
        if (runningTurnStartedAtNanos <= 0L) {
            return 0L;
        }
        return Math.max(System.nanoTime() - runningTurnStartedAtNanos, 0L) / NANOS_PER_SECOND;
    }

    /**
     * 渲染紧凑输入行，不再给输入框增加额外边框。
     *
     * @return 可见 composer 文本。
     */
    private String renderComposer() {
        String taskText = normalizeRendererNewlines(textarea.value());
        String cursorCell = textarea.cursor().isBlink() ? " " : VISIBLE_CURSOR;
        // 真实 TTY 下 tui4j Textarea.view() 会附带光标样式和填充行；底部 composer 必须稳定为单行。
        if (taskText.isBlank()) {
            return renderEmptyComposer(cursorCell);
        }
        return renderEditableComposer(taskText, cursorCell);
    }

    /**
     * 按 Textarea 的真实行列光标渲染单行 composer；左右/上下键只改变 Textarea 状态，这里负责把状态反映到自绘光标。
     *
     * @param taskText 输入框原始文本，可能包含换行。
     * @param cursorCell 当前光标可见或隐藏占位。
     * @return 带真实光标位置的单行 composer。
     */
    private String renderEditableComposer(String taskText, String cursorCell) {
        String[] lines = taskText.split(RENDER_NEWLINE, -1);
        int row = Math.max(0, Math.min(textarea.line(), lines.length - 1));
        int cursorIndex = charIndexForCellOffset(lines[row], textarea.lineInfo().charOffset());
        StringBuilder beforeCursor = new StringBuilder();
        for (int index = 0; index < row; index++) {
            if (!beforeCursor.isEmpty()) {
                beforeCursor.append(' ');
            }
            beforeCursor.append(lines[index]);
        }
        if (row > 0) {
            beforeCursor.append(' ');
        }
        beforeCursor.append(lines[row], 0, cursorIndex);

        StringBuilder afterCursor = new StringBuilder(lines[row].substring(cursorIndex));
        for (int index = row + 1; index < lines.length; index++) {
            afterCursor.append(' ');
            afterCursor.append(lines[index]);
        }
        return truncateVisualLine("› " + beforeCursor + cursorCell + afterCursor);
    }

    /**
     * 将 textarea 的单行 cell 偏移转换为 Java 字符串下标，确保中文宽字符前后移动时光标仍落在字符边界。
     *
     * @param value 当前 textarea 行文本。
     * @param cellOffset 光标相对当前行的终端列偏移。
     * @return Java 字符串下标。
     */
    private int charIndexForCellOffset(String value, int cellOffset) {
        int columns = 0;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            int width = codePointWidth(codePoint);
            if (columns + width > cellOffset) {
                return offset;
            }
            columns += width;
            offset += Character.charCount(codePoint);
            if (columns >= cellOffset) {
                return offset;
            }
        }
        return value.length();
    }

    /**
     * 渲染空输入状态；先按纯可见文本截断，再给 placeholder 加灰，避免 ANSI 控制串参与宽度计算。
     *
     * @param cursorCell 当前光标可见或隐藏占位。
     * @return 空 composer 可见行。
     */
    private String renderEmptyComposer(String cursorCell) {
        String prefix = "› " + cursorCell;
        String truncated = truncateVisualLine(prefix + COMPOSER_PLACEHOLDER);
        if (truncated.length() <= prefix.length()) {
            return truncated;
        }
        return prefix + stylePlaceholder(truncated.substring(prefix.length()));
    }

    /**
     * 将空输入提示渲染为灰色文本，使它看起来像 placeholder 而不是一条真实消息。
     *
     * @param placeholder 已按终端宽度截断的可见提示行。
     * @return 带 ANSI 灰色样式的提示行。
     */
    private String stylePlaceholder(String placeholder) {
        return styleDim(placeholder);
    }

    /**
     * 渲染灰色弱提示；用于 placeholder 和运行中 Working 行。
     *
     * @param text 原始文本。
     * @return 带 ANSI 灰色样式的文本。
     */
    private String styleDim(String text) {
        return ANSI_DIM_PLACEHOLDER + text + ANSI_RESET;
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
     * 尝试把当前 Slash Command 前缀补全为唯一命令；多候选时只保留面板，不擅自选择。
     *
     * @return true 表示已经消费 Tab 事件。
     */
    private boolean completeUniqueSlashCommand() {
        String value = normalizeRendererNewlines(textarea.value()).trim();
        if (!value.startsWith("/")) {
            return false;
        }
        String keyword = slashCommandKeyword(value);
        List<SlashCommandDisplay> matches = slashCommandDisplays().stream()
            .filter(command -> command.command().substring(1).toLowerCase().startsWith(keyword))
            .toList();
        if (matches.size() != 1) {
            return false;
        }
        textarea.setValue(matches.getFirst().command());
        return true;
    }

    /**
     * 渲染输入中的命令候选；保持纯文本而非复杂交互控件，适配普通终端和测试输出。
     *
     * @return 命令候选文本。
     */
    private String renderSlashCommandPanel() {
        String keyword = slashCommandKeyword(normalizeRendererNewlines(textarea.value()).trim());
        List<String> commands = slashCommandDisplays().stream()
            .filter(command -> keyword.isBlank() || command.searchText().contains(keyword))
            .map(SlashCommandDisplay::line)
            .toList();
        if (commands.isEmpty()) {
            return styleDim("  未匹配到命令");
        }
        List<String> lines = new ArrayList<>();
        lines.add("  命令");
        for (String command : commands) {
            lines.add("  " + command);
        }
        return styleDim(String.join(RENDER_NEWLINE, lines));
    }

    /**
     * 提取 Slash Command 搜索关键字，去掉前导 `/` 并统一小写。
     *
     * @param value 当前输入框文本。
     * @return 用于面板过滤和 Tab 补全的关键字。
     */
    private String slashCommandKeyword(String value) {
        return value.replaceFirst("^/+", "").toLowerCase();
    }

    /**
     * 返回本地控制命令和后端治理命令的统一展示数据；面板和补全共享这份列表。
     *
     * @return 可展示和匹配的命令列表。
     */
    private List<SlashCommandDisplay> slashCommandDisplays() {
        List<SlashCommandDisplay> commands = new ArrayList<>();
        commands.add(new SlashCommandDisplay("/login", "/login   登录 CodingX"));
        commands.add(new SlashCommandDisplay("/logout", "/logout  退出登录"));
        commands.add(new SlashCommandDisplay("/help", "/help    查看命令列表"));
        for (CliSlashCommand command : backendSlashCommands) {
            String commandText = "/" + command.commandCode();
            commands.add(new SlashCommandDisplay(commandText, formatBackendSlashCommand(command)));
        }
        return commands;
    }

    /**
     * Slash Command 展示项；command 用于补全，line 用于候选面板。
     */
    private record SlashCommandDisplay(String command, String line) {

        private String searchText() {
            return line.toLowerCase();
        }
    }

    /**
     * 首次构造 TUI 时读取后端命令目录；读取失败由目录实现降级为空列表。
     */
    private List<CliSlashCommand> loadBackendSlashCommands(SlashCommandCatalog catalog) {
        try {
            return catalog.listCommands().stream()
                .filter(command -> command != null && command.commandCode() != null && !command.commandCode().isBlank())
                .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    /**
     * 判断输入的前导 Slash token 是否来自后端治理命令；命中时由聊天流请求负责结构化提交。
     */
    private boolean isBackendSlashCommand(String normalizedCommand) {
        String commandToken = normalizedCommand.split("\\s+", 2)[0].replaceFirst("^/+", "");
        if (commandToken.isBlank()) {
            return false;
        }
        return backendSlashCommands.stream()
            .anyMatch(command -> commandToken.equalsIgnoreCase(command.commandCode()));
    }

    /**
     * 格式化后端命令候选，保持与本地控制命令同一面板密度。
     */
    private String formatBackendSlashCommand(CliSlashCommand command) {
        String displayName = command.displayName() == null || command.displayName().isBlank()
            ? "/" + command.commandCode()
            : command.displayName();
        String description = command.description() == null ? "" : command.description().trim();
        if (description.isBlank()) {
            return displayName;
        }
        return String.format(Locale.ROOT, "%-8s %s", displayName, description);
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
     * 计算输入框、状态栏占用的行数；活动区使用紧凑单换行，避免等待提示把最近问题挤出尾部可见区。
     *
     * @return 非 transcript 区域占用行数。
     */
    private int nonTranscriptLineCount() {
        int currentQuestionAnchorLines = shouldRenderCurrentQuestionAnchor()
            ? renderedVisualLineCount(renderCurrentQuestionAnchor())
            : 0;
        return currentQuestionAnchorLines
            + renderedVisualLineCount(renderComposer())
            + renderedVisualLineCount(renderStatusBar());
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
        if (isTurnRunning()) {
            appendVisualRows(rows, renderWorkingLine(), -1);
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
            if (isAnsiSequenceStart(value, offset)) {
                int ansiEnd = ansiSequenceEnd(value, offset);
                row.append(value, offset, ansiEnd);
                offset = ansiEnd;
                continue;
            }
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
            if (isAnsiSequenceStart(logicalLine, offset)) {
                int ansiEnd = ansiSequenceEnd(logicalLine, offset);
                row.append(logicalLine, offset, ansiEnd);
                offset = ansiEnd;
                continue;
            }
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
     * 识别 ANSI CSI 控制序列起点；这些序列只影响样式，不应参与终端列宽计算。
     *
     * @param value 当前渲染文本。
     * @param offset 待检查的字符串下标。
     * @return true 表示当前位置是 ESC[ 开头的 ANSI CSI 序列。
     */
    private boolean isAnsiSequenceStart(String value, int offset) {
        return value.charAt(offset) == '\u001B'
            && offset + 1 < value.length()
            && value.charAt(offset + 1) == '[';
    }

    /**
     * 找到 ANSI CSI 控制序列的结束位置；异常截断时消费到行尾，避免把残缺控制串拆成可见字符。
     *
     * @param value 当前渲染文本。
     * @param offset ESC 字符所在下标。
     * @return 序列结束后的下标。
     */
    private int ansiSequenceEnd(String value, int offset) {
        int cursor = offset + 2;
        while (cursor < value.length()) {
            char marker = value.charAt(cursor++);
            if (marker >= '@' && marker <= '~') {
                return cursor;
            }
        }
        return value.length();
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
