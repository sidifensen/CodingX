# Java CLI 基础终端

## 功能用途

提供 CodingX 独立 CLI 的 Codex 风格 TUI 终端入口，让开发者输入 `codingx` 后在普通终端屏幕中进入交互模式：shell 中的 `codingx` 命令上下文会保留，启动页只展示紧凑信息卡、Tip、单行输入提示和简洁状态行。当前版本已经把 TUI 输入对接到现有后端 `/api/chat/stream` SSE 对话流：CLI 会读取用户主目录配置中的后端地址和 satoken，以本地运行目标提交当前仓库路径，并把后端返回的流式事件映射为终端 transcript。

## 使用入口

- `script/install-codingx.ps1`：本地安装入口，先在 `cli` 目录执行 `mvn -q package` 生成 `target/codingx.jar`，再复制到用户目录 `.codingx/bin` 并生成 `codingx.cmd` / `codingx.ps1`。安装脚本会把 `.codingx/bin` 写入用户级 PATH，新终端可直接输入 `codingx`。
- `codingx login <serverUrl> <satoken>`：把后端地址和登录令牌保存到用户主目录 `.codingx/cli.yml`，避免 satoken 写进项目仓库。
- `codingx`：默认进入 TUI 交互界面，顶部展示 `>_ CodingX CLI (v0.1.0)` 启动卡片、后端选模提示和当前工作区；任务只能在 `› Write tests for @filename` 输入行里提交，并通过后端聊天流执行。CLI 不再展示没有真实来源的模型名、MCP 连接数或工具数量。
- `codingx tui`：显式进入同一个 TUI 交互界面，和默认入口一致。
- `codingx exec "<任务>"`：已移除非交互任务模式，返回中文提示，要求用户进入 TUI 后提交任务。
- `codingx resume` / `codingx sessions`：不作为独立会话产品模式暴露，当前同样返回 TUI-only 提示；会话列表和恢复后续应在 TUI 内实现。

## 核心流程

1. 开发者执行 `script/install-codingx.ps1` 后，脚本先定位项目根目录和 `cli` 子目录，再运行 `mvn -q package`。Maven Shade Plugin 会把 CLI 代码和运行依赖打进 `target/codingx.jar`，并在 Manifest 写入 `Main-Class=com.codingx.cli.CodingXCli`；打包失败或 jar 不存在时脚本直接抛出中文错误，不会写入半成品启动器。
2. 打包成功后，安装脚本创建用户目录 `.codingx/bin`，复制 `target/codingx.jar` 为 `.codingx/bin/codingx.jar`，再生成 `codingx.cmd` 和 `codingx.ps1` 两种 Windows 启动器。两个启动器只做一件事：把当前命令行参数原样转发给 `java -jar ~/.codingx/bin/codingx.jar`；脚本随后把 `.codingx/bin` 写入用户级 PATH，并临时补到当前 PowerShell 进程 PATH，方便安装后立即验证。
3. 用户运行 `codingx` 命令后，Windows 通过 PATH 找到 `codingx.cmd`，`cmd` 启动 `java -jar codingx.jar`，再进入 `CodingXCli.main`。`CodingXCli` 使用用户主目录创建 `CliConfigStore`，并用当前工作目录、`BackendChatEventSource` 和 `TerminalRenderer` 组装 `CodingXTuiLauncher`；这意味着从任意仓库目录输入 `codingx` 时，后端收到的 `repositoryPath` 就是用户当前目录。
4. 用户执行 `login` 时，`CliCommandRunner` 校验 `serverUrl` 和 `satoken` 参数数量；参数缺失时返回中文用法提示，参数完整时通过 `CliConfigStore` 使用 SnakeYAML 写入用户主目录配置。该流程不会读取或写入当前工作区文件，避免登录令牌进入 Git 仓库。
5. 用户执行 `codingx` 或 `codingx tui` 时，`CliCommandRunner` 调用 `TuiLauncher.launch()`，真实实现先通过 `CodingXTuiModel.startupBanner()` 把 `>_ CodingX CLI (v0.1.0)` 启动卡片和 `Tip: Build faster with CodingX.` 写入普通 shell 输出，再启动 tui4j `Program.run()` 维护后续交互区。该拆分用于规避 Windows 终端下 tui4j 普通屏幕 renderer 按活动区域高度裁剪首帧的问题，确保 logo 和启动卡片不会被裁掉。`CodingXTuiModel.view()` 的活动区只渲染真实 transcript、单行 composer 和底部状态行；初始态不会追加假 transcript，也不会用空白 viewport 把输入行推到窗口底部。`TuiStatusBarRenderer` 只展示后端选模来源、当前工作区、运行状态和 `Plan mode` / `Chat mode` 文案；模型名、MCP 连接数和工具数量没有后端真实字段时不会显示。
6. 用户在 `Textarea` 输入任务后按 Enter，`CodingXTuiModel` 会先于 `textarea.update(...)` 拦截提交键，并兼容 JLine/Windows 终端可能上报的 CR(`enter`) 和 LF(`ctrl+j`) 两种回车类型；空任务直接忽略，非空任务先追加 `> 用户任务` 并把状态切到 `running`，再重置底部输入框。这个顺序保证回车不会被 Textarea 当成编辑换行吞掉，用户提交的原文会稳定出现在 transcript 中。当事件源支持流式推送时，模型把任务文本和当前工作区传给 `BackendChatEventSource`，后端流消费线程通过 tui4j `Program.send(...)` 把每个 `AgentEvent` 送回主更新循环；连续 `ASSISTANT_DELTA` 会更新同一个助手回答块，避免后端小片段在 TUI 中变成散乱项目符号列表。
7. `BackendChatEventSource` 每轮请求都会从 `CliConfigStore` 读取最新 `serverUrl`、`token` 和 `lastSessionId`，构造 `GET /api/chat/stream` 请求。请求固定携带 `question`、`runtimeTarget=local` 和当前工作区绝对路径 `repositoryPath`；当 `lastSessionId` 是数值字符串时，额外携带 `conversationId` 续接后端会话；token 非空时写入 `satoken` header，token 为空时交给后端返回统一登录错误。
8. 后端返回 SSE 后，`SseEventParser` 按空行拆分 `event:` / `data:` 块，`BackendChatEventMapper` 将 `meta`、`message`、`thinking`、`tool-call`、`mcp-call`、`finish`、`reject`、`queued`、`queue-accepted`、`error`、`done` 映射为 CLI 现有 `AgentEvent`。`meta.conversationId` 或 `finish.conversationId` 到达后，事件源会把数值会话 ID 写回用户主目录配置的 `lastSessionId`，下一次 TUI 输入自然延续同一个后端会话。
9. `TuiTranscriptRenderer` 把非活动流事件渲染成对话流和工具状态行；助手正文由 `CodingXTuiModel` 先合并活动回答块再写入 transcript，思考增量显示为 `Thinking:`，工具事件优先展示后端 `displayName` 或 `toolId`，完成事件显示 `Task completed: COMPLETED`，错误事件显示 `! Error` 并让底部状态变为 `error`。`shift+tab` 当前只在本地切换 `Plan mode` / `Chat mode` 文案，不触发真实规划策略。
10. 用户执行 `exec`、`resume` 或 `sessions` 时，命令分发器不会启动任务或独立会话流程，而是返回非零退出码和 TUI-only 中文提示。这样 CLI 保持单一产品形态，后续会话恢复、会话列表和权限审批都应接入 TUI 内部工作流。

## 关键文件

- `cli/pom.xml`：通过 Maven Shade Plugin 在 `package` 阶段生成可执行 `target/codingx.jar`。
- `script/install-codingx.ps1`：Windows 本地安装脚本，负责打包、复制 jar、生成启动器和写入用户 PATH。
- `cli/src/main/java/com/codingx/cli/CodingXCli.java`：CLI 进程入口，负责组装配置读写器和 TUI 启动器。
- `cli/src/main/java/com/codingx/cli/command/CliCommandRunner.java`：命令解析和流程编排，覆盖 `login`、默认 TUI、显式 `tui` 和非交互命令拒绝。
- `cli/src/main/java/com/codingx/cli/tui/TuiLauncher.java`：TUI 启动边界，便于命令层单测替换真实终端入口。
- `cli/src/main/java/com/codingx/cli/tui/CodingXTuiLauncher.java`：tui4j `Program` 启动器，负责先打印固定启动卡片，再在普通屏幕运行交互区并保留 shell 命令上下文。
- `cli/src/main/java/com/codingx/cli/tui/CodingXTuiModel.java`：TUI 状态模型，维护启动 banner、事件窗口、输入框、任务提交、活动助手回答块、计划模式和状态栏。
- `cli/src/main/java/com/codingx/cli/tui/TuiHeaderRenderer.java`：顶部 Codex 风格启动卡片渲染，集中展示版本、后端选模来源和当前工作区，只展示真实可确认的 UI 数据。
- `cli/src/main/java/com/codingx/cli/tui/TuiTranscriptRenderer.java`：TUI 对话流渲染，把 `AgentEvent` 转成助手消息、工具状态、命令输出、完成和错误行。
- `cli/src/main/java/com/codingx/cli/tui/TuiStatusBarRenderer.java`：底部状态栏渲染，展示后端选模来源、当前工作区、运行状态和键盘提示，不承载本地假模型名。
- `cli/src/main/java/com/codingx/cli/agent/AgentEvent.java`：Agent 事件信封，承载会话、轮次、序号、类型、载荷和创建时间。
- `cli/src/main/java/com/codingx/cli/agent/StreamingAgentEventSource.java`：支持增量推送的事件源接口，TUI 用它把后端 SSE 事件实时送回主更新循环。
- `cli/src/main/java/com/codingx/cli/backend/BackendChatEventSource.java`：真实后端聊天流客户端，负责读取 CLI 配置、构造 `/api/chat/stream` 请求、携带 `satoken`、消费 SSE 并写回最近会话。
- `cli/src/main/java/com/codingx/cli/backend/SseEventParser.java`：SSE 文本解析器，按事件块输出后端事件名和 data。
- `cli/src/main/java/com/codingx/cli/backend/BackendChatEventMapper.java`：把后端聊天事件映射为 CLI `AgentEvent`。
- `cli/src/main/java/com/codingx/cli/tui/AgentEventsMessage.java`：后台事件线程进入 tui4j 主更新循环的消息类型。
- `cli/src/main/java/com/codingx/cli/agent/MockAgentEventSource.java`：测试和本地回归使用的 mock 事件流，不再作为生产入口事件源。
- `cli/src/main/java/com/codingx/cli/render/TerminalRenderer.java`：终端文本渲染，负责把统一事件协议转成可读输出。
- `cli/src/main/java/com/codingx/cli/config/CliConfigStore.java`：用户级配置读写，令牌只保存在用户主目录。
- `cli/src/test/java/com/codingx/cli/CliDistributionTest.java`：CLI 分发契约测试，验证可执行 jar 配置和 Windows 安装脚本关键行为。

## 关键数据结构

- `AgentEventType`：定义 CLI MVP 当前识别的事件类型，包括 `SESSION_STARTED`、`TURN_STARTED`、`ASSISTANT_DELTA`、`TOOL_STARTED`、`COMMAND_OUTPUT_DELTA`、`APPROVAL_REQUESTED`、`TURN_COMPLETED`、`ERROR` 等。
- `CliConfig`：保存后端地址、satoken、审批策略和最近会话标识；首次运行时默认后端地址为 `http://localhost:5001`，审批策略为 `conservative`。
- `codingx.jar`：Shade 打包后的 CLI 可执行 jar，安装脚本会复制到用户目录 `.codingx/bin/codingx.jar`。
- `codingx.cmd` / `codingx.ps1`：Windows 启动器，负责从 PATH 接收 `codingx` 命令并转发到 `java -jar`。
- `AgentEventSource`：事件源抽象边界；生产实现是 `BackendChatEventSource`，测试可继续使用 `MockAgentEventSource`。
- `StreamingAgentEventSource`：流式事件源扩展；同步 `startTurn` 仍会收集事件列表，流式 `startTurn(..., Consumer<AgentEvent>)` 用于 TUI 实时刷新。
- `SseEvent`：后端 SSE 事件块，包含事件名和原始 data 文本。
- `CodingXTuiModel`：TUI 内存状态，包含 `Viewport` 事件窗口、`Textarea` 任务输入框、当前工作区、活动助手回答块、`Plan mode` / `Chat mode`、运行状态和后端流消费线程；UI 状态只在进程内维护，会话续接状态写入用户主目录 CLI 配置。
- `TuiTranscriptRenderer`：TUI 展示层事件映射；工具名和工具结果优先来自后端 payload，没有真实后端字段的 MCP 连接状态、工具数量和模型名不会在 TUI 中硬编码展示。

## 测试与验证

- `cd cli && mvn test`
- `cd cli && mvn package`
- `powershell -ExecutionPolicy Bypass -File script/install-codingx.ps1 -ProjectRoot D:\code\CodingX`
- `where codingx`：验证命令解析到用户目录 `.codingx/bin/codingx.cmd`。
- `codingx exec smoke-test`：验证 PATH 命令进入 CLI 命令分发，并继续拒绝非交互任务模式。
