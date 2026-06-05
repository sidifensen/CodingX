# Java CLI 基础终端

## 功能用途

提供 CodingX 独立 CLI 的截图风格 TUI 终端入口，让开发者输入 `codingx` 进入全屏终端后，在输入框里提交任务，并在同一界面查看类似 MewCode / Claude Code / Codex CLI 的品牌头、对话流、工具状态、输入栏和模式栏。当前版本已经把 TUI 输入对接到现有后端 `/api/chat/stream` SSE 对话流：CLI 会读取用户主目录配置中的后端地址和 satoken，以本地运行目标提交当前仓库路径，并把后端返回的流式事件映射为终端 transcript。

## 使用入口

- `codingx login <serverUrl> <satoken>`：把后端地址和登录令牌保存到用户主目录 `.codingx/cli.yml`，避免 satoken 写进项目仓库。
- `codingx`：默认进入 TUI 交互界面，顶部展示 `CodingX v0.1.0`、当前模型 `GLM-5.1`、工作区和 MCP/tools 状态；任务只能在 TUI 输入框里提交，并通过后端聊天流执行。
- `codingx tui`：显式进入同一个 TUI 交互界面，和默认入口一致。
- `codingx exec "<任务>"`：已移除非交互任务模式，返回中文提示，要求用户进入 TUI 后提交任务。
- `codingx resume` / `codingx sessions`：不作为独立会话产品模式暴露，当前同样返回 TUI-only 提示；会话列表和恢复后续应在 TUI 内实现。

## 核心流程

1. 用户运行 CLI 命令后，`CodingXCli` 使用用户主目录创建 `CliConfigStore`，并用当前工作目录、`BackendChatEventSource` 和 `TerminalRenderer` 组装 `CodingXTuiLauncher`。`CliCommandRunner` 只负责识别 `login`、默认入口和 `tui` 入口，不承载模型决策、TUI 布局或工具执行细节；`exec`、`resume`、`sessions` 都不会绕过 TUI 直接发起任务。
2. 用户执行 `login` 时，`CliCommandRunner` 校验 `serverUrl` 和 `satoken` 参数数量；参数缺失时返回中文用法提示，参数完整时通过 `CliConfigStore` 使用 SnakeYAML 写入用户主目录配置。该流程不会读取或写入当前工作区文件，避免登录令牌进入 Git 仓库。
3. 用户执行 `codingx` 或 `codingx tui` 时，`CliCommandRunner` 调用 `TuiLauncher.launch()`，真实实现通过 tui4j `Program` 和 alt screen 接管当前终端。`CodingXTuiModel` 初始化截图风格视图，顶部由 `TuiHeaderRenderer` 渲染品牌、模型、工作区和 mock 工具状态，底部由 `TuiStatusBarRenderer` 渲染 `Plan on (shift+tab to cycle)`、运行状态和模型名。
4. 用户在 `Textarea` 输入任务后按 Enter，`CodingXTuiModel` 会 trim 输入；空任务直接忽略，非空任务先追加 `> 用户任务` 并把状态切到 `running`。当事件源支持流式推送时，模型把任务文本和当前工作区传给 `BackendChatEventSource`，后端流消费线程通过 tui4j `Program.send(...)` 把每个 `AgentEvent` 送回主更新循环，避免网络读取阻塞 TUI。
5. `BackendChatEventSource` 每轮请求都会从 `CliConfigStore` 读取最新 `serverUrl`、`token` 和 `lastSessionId`，构造 `GET /api/chat/stream` 请求。请求固定携带 `question`、`runtimeTarget=local` 和当前工作区绝对路径 `repositoryPath`；当 `lastSessionId` 是数值字符串时，额外携带 `conversationId` 续接后端会话；token 非空时写入 `satoken` header，token 为空时交给后端返回统一登录错误。
6. 后端返回 SSE 后，`SseEventParser` 按空行拆分 `event:` / `data:` 块，`BackendChatEventMapper` 将 `meta`、`message`、`thinking`、`tool-call`、`mcp-call`、`finish`、`reject`、`queued`、`queue-accepted`、`error`、`done` 映射为 CLI 现有 `AgentEvent`。`meta.conversationId` 或 `finish.conversationId` 到达后，事件源会把数值会话 ID 写回用户主目录配置的 `lastSessionId`，下一次 TUI 输入自然延续同一个后端会话。
7. `TuiTranscriptRenderer` 把事件渲染成对话流和工具状态行；助手正文用缩进对话行展示，思考增量显示为 `Thinking:`，工具事件优先展示后端 `displayName` 或 `toolId`，完成事件显示 `Task completed: COMPLETED`，错误事件显示 `! Error` 并让底部状态变为 `error`。`shift+tab` 当前只在本地切换 `Plan on/off` 文案，不触发真实规划策略。
8. 用户执行 `exec`、`resume` 或 `sessions` 时，命令分发器不会启动任务或独立会话流程，而是返回非零退出码和 TUI-only 中文提示。这样 CLI 保持单一产品形态，后续会话恢复、会话列表和权限审批都应接入 TUI 内部工作流。

## 关键文件

- `cli/src/main/java/com/codingx/cli/CodingXCli.java`：CLI 进程入口，负责组装配置读写器和 TUI 启动器。
- `cli/src/main/java/com/codingx/cli/command/CliCommandRunner.java`：命令解析和流程编排，覆盖 `login`、默认 TUI、显式 `tui` 和非交互命令拒绝。
- `cli/src/main/java/com/codingx/cli/tui/TuiLauncher.java`：TUI 启动边界，便于命令层单测替换真实全屏终端。
- `cli/src/main/java/com/codingx/cli/tui/CodingXTuiLauncher.java`：tui4j `Program` 启动器，负责进入 alt screen。
- `cli/src/main/java/com/codingx/cli/tui/CodingXTuiModel.java`：TUI 状态模型，维护截图风格 header、事件窗口、输入框、任务提交、计划模式和状态栏。
- `cli/src/main/java/com/codingx/cli/tui/TuiHeaderRenderer.java`：顶部品牌区渲染，集中展示版本、模型、工作区和 mock MCP/tools 状态。
- `cli/src/main/java/com/codingx/cli/tui/TuiTranscriptRenderer.java`：TUI 对话流渲染，把 `AgentEvent` 转成助手消息、工具状态、命令输出、完成和错误行。
- `cli/src/main/java/com/codingx/cli/tui/TuiStatusBarRenderer.java`：底部模式栏渲染，展示 `Plan on/off`、运行状态和模型名。
- `cli/src/main/java/com/codingx/cli/agent/AgentEvent.java`：Agent 事件信封，承载会话、轮次、序号、类型、载荷和创建时间。
- `cli/src/main/java/com/codingx/cli/agent/StreamingAgentEventSource.java`：支持增量推送的事件源接口，TUI 用它把后端 SSE 事件实时送回主更新循环。
- `cli/src/main/java/com/codingx/cli/backend/BackendChatEventSource.java`：真实后端聊天流客户端，负责读取 CLI 配置、构造 `/api/chat/stream` 请求、携带 `satoken`、消费 SSE 并写回最近会话。
- `cli/src/main/java/com/codingx/cli/backend/SseEventParser.java`：SSE 文本解析器，按事件块输出后端事件名和 data。
- `cli/src/main/java/com/codingx/cli/backend/BackendChatEventMapper.java`：把后端聊天事件映射为 CLI `AgentEvent`。
- `cli/src/main/java/com/codingx/cli/tui/AgentEventsMessage.java`：后台事件线程进入 tui4j 主更新循环的消息类型。
- `cli/src/main/java/com/codingx/cli/agent/MockAgentEventSource.java`：测试和本地回归使用的 mock 事件流，不再作为生产入口事件源。
- `cli/src/main/java/com/codingx/cli/render/TerminalRenderer.java`：终端文本渲染，负责把统一事件协议转成可读输出。
- `cli/src/main/java/com/codingx/cli/config/CliConfigStore.java`：用户级配置读写，令牌只保存在用户主目录。

## 关键数据结构

- `AgentEventType`：定义 CLI MVP 当前识别的事件类型，包括 `SESSION_STARTED`、`TURN_STARTED`、`ASSISTANT_DELTA`、`TOOL_STARTED`、`COMMAND_OUTPUT_DELTA`、`APPROVAL_REQUESTED`、`TURN_COMPLETED`、`ERROR` 等。
- `CliConfig`：保存后端地址、satoken、审批策略和最近会话标识；首次运行时默认后端地址为 `http://localhost:5001`，审批策略为 `conservative`。
- `AgentEventSource`：事件源抽象边界；生产实现是 `BackendChatEventSource`，测试可继续使用 `MockAgentEventSource`。
- `StreamingAgentEventSource`：流式事件源扩展；同步 `startTurn` 仍会收集事件列表，流式 `startTurn(..., Consumer<AgentEvent>)` 用于 TUI 实时刷新。
- `SseEvent`：后端 SSE 事件块，包含事件名和原始 data 文本。
- `CodingXTuiModel`：TUI 内存状态，包含 `Viewport` 事件窗口、`Textarea` 任务输入框、当前工作区、模型名、`Plan on/off`、运行状态和后端流消费线程；UI 状态只在进程内维护，会话续接状态写入用户主目录 CLI 配置。
- `TuiTranscriptRenderer`：TUI 展示层事件映射；工具名和工具结果优先来自后端 payload，MCP 连接状态仍是 header 区域的展示文案，不能作为真实遥测读取。

## 测试与验证

- `cd cli && mvn test`
- `cd cli && mvn -q exec:java "-Dexec.mainClass=com.codingx.cli.CodingXCli" "-Dexec.args=exec 分析这个项目"`：验证非交互任务模式被拒绝并返回 TUI-only 提示。
