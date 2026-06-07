# Java CLI 基础终端

## 功能用途

提供 CodingX 独立 CLI 的 Codex 风格 TUI 终端入口，让开发者输入 `codingx` 后在普通终端屏幕中进入交互模式：shell 中的 `codingx` 命令上下文会保留，启动页只展示紧凑信息卡、Tip、单行输入提示和简洁状态行。当前版本已经把 TUI 输入对接到现有后端 `/api/chat/stream` SSE 对话流，并新增浏览器登录授权：CLI 会在本机启动临时 loopback 回调服务，浏览器访问用户端 `/cli-login` 完成账号确认，随后 CLI 把一次性授权码兑换为 satoken 并保存到用户主目录配置。

## 使用入口

- `script/install-codingx.ps1`：本地安装入口，先在 `cli` 目录执行 `mvn -q package` 生成 `target/codingx.jar`，再复制到用户目录 `.codingx/bin` 并生成 `codingx.cmd` / `codingx.ps1`。安装脚本会把 `.codingx/bin` 写入用户级 PATH，新终端可直接输入 `codingx`。
- `codingx auth login`：默认登录入口，CLI 在 `127.0.0.1:<randomPort>/callback` 启动临时回调服务，打开浏览器进入用户端 `/cli-login`，授权成功后用一次性 code 兑换 satoken 并写入 `.codingx/cli.yml`。
- `codingx auth login --device`：无浏览器或远程终端兜底入口，CLI 先从后端获取设备码，用户在浏览器打开 `/cli-login?deviceCode=...` 授权，终端按后端返回的轮询间隔等待结果。
- `codingx logout` / `codingx auth logout`：清理本机 CLI 登录态，只删除用户主目录配置中的 token 和最近会话，不修改后端地址与审批策略。
- `codingx login <serverUrl> <satoken>`：保留诊断用手动登录入口，把后端地址和登录令牌保存到用户主目录 `.codingx/cli.yml`，避免 satoken 写进项目仓库。
- `/cli-login`：用户端前端专用授权页，独立于聊天工作台渲染；已登录时直接确认授权，未登录时先完成网页账号密码登录，再继续 CLI 授权。
- `codingx`：默认进入 TUI 交互界面，顶部展示 `>_ CodingX CLI (v0.1.0)` 启动卡片、后端选模提示和当前工作区；任务在 `› 输入任务，/ 查看命令` 输入行里提交，并通过后端聊天流执行。输入 `/` 会在输入区上方展示 `/login`、`/logout`、`/help` 命令候选，命令由 TUI 本地消费，不会发送到聊天接口。CLI 不再展示没有真实来源的模型名、MCP 连接数或工具数量。
- `codingx tui`：显式进入同一个 TUI 交互界面，和默认入口一致。
- `codingx exec "<任务>"`：已移除非交互任务模式，返回中文提示，要求用户进入 TUI 后提交任务。
- `codingx resume` / `codingx sessions`：不作为独立会话产品模式暴露，当前同样返回 TUI-only 提示；会话列表和恢复后续应在 TUI 内实现。

## 核心流程

1. 开发者执行 `script/install-codingx.ps1` 后，脚本先定位项目根目录和 `cli` 子目录，再运行 `mvn -q package`。Maven Shade Plugin 会把 CLI 代码和运行依赖打进 `target/codingx.jar`，并在 Manifest 写入 `Main-Class=com.codingx.cli.CodingXCli`；打包失败或 jar 不存在时脚本直接抛出中文错误，不会写入半成品启动器。
2. 打包成功后，安装脚本创建用户目录 `.codingx/bin`，复制 `target/codingx.jar` 为 `.codingx/bin/codingx.jar`，再生成 `codingx.cmd` 和 `codingx.ps1` 两种 Windows 启动器。两个启动器只做一件事：把当前命令行参数原样转发给 `java -jar ~/.codingx/bin/codingx.jar`；脚本随后把 `.codingx/bin` 写入用户级 PATH，并临时补到当前 PowerShell 进程 PATH，方便安装后立即验证。
3. 用户运行 `codingx` 命令后，Windows 通过 PATH 找到 `codingx.cmd`，`cmd` 启动 `java -jar codingx.jar`，再进入 `CodingXCli.main`。`CodingXCli` 使用用户主目录创建 `CliConfigStore`，并用当前工作目录、`BackendChatEventSource` 和 `TerminalRenderer` 组装 `CodingXTuiLauncher`；这意味着从任意仓库目录输入 `codingx` 时，后端收到的 `repositoryPath` 就是用户当前目录。
4. 用户执行 `codingx auth login` 时，`CliCommandRunner` 进入认证分支并调用 `CliAuthService.loginWithBrowser()`。服务先读取 `.codingx/cli.yml` 中的后端地址，生成 `state`、PKCE `codeVerifier/codeChallenge`，再用 `LoopbackCallbackServer` 只绑定 `127.0.0.1` 的随机端口；随后按后端地址推导用户端前端地址，本地开发会从 `5001` 映射到 `5002`，正式 `api.` 子域会映射到根域。
5. 浏览器打开 `/cli-login?redirectUri=...&state=...&codeChallenge=...` 后，用户端 `CliLoginView` 会先恢复或创建网页登录态，再携带 satoken 调用 `POST /api/auth/cli/authorize`。后端 `CliAuthApplicationService` 校验 `redirectUri` 必须是 `http://127.0.0.1:<port>/callback`、`localhost` 或 `[::1]` 的 callback 路径，并把一次性 code、state、codeChallenge、userId 和过期时间写入内存状态；前端拿到 code 后只跳回 CLI 本机回调地址，不在 URL 中暴露真实 satoken。
6. CLI 收到本机回调后，`LoopbackCallbackServer` 校验回调 state 并返回 code，`CliAuthService` 再调用 `POST /api/auth/cli/token` 提交 code、state 和原始 verifier。后端读取即消费授权码，依次校验 state 与 PKCE S256 challenge；校验通过后重新加载用户、确认账号有效，并通过现有 Sa-Token 登录网关创建 CLI 专用 satoken。CLI 解析统一 `ApiResponse`，把 token 写入用户主目录配置，同时清空 `lastSessionId`，避免新 token 续接旧账号会话。
7. 用户执行 `codingx auth login --device` 时，CLI 调用 `POST /api/auth/cli/device/start` 获取内部 `deviceCode`、用户可读 `userCode`、验证页路径、过期时间和轮询间隔。终端展示 `/cli-login?deviceCode=...`，浏览器页登录后调用 `POST /api/auth/cli/device/authorize` 把 userCode 绑定到当前网页登录用户；CLI 按 `pollIntervalSeconds` 调用 `POST /api/auth/cli/device/token`，pending 时继续等待，approved 时消费设备码并保存 token，超时或后端错误时展示后端中文 message。
8. 用户执行诊断用 `codingx login <serverUrl> <satoken>` 时，`CliCommandRunner` 校验 `serverUrl` 和 `satoken` 参数数量；参数缺失时返回中文用法提示，参数完整时通过 `CliConfigStore` 使用 SnakeYAML 写入用户主目录配置。该流程不会读取或写入当前工作区文件，避免登录令牌进入 Git 仓库。
9. 用户执行 `codingx` 或 `codingx tui` 时，`CliCommandRunner` 调用 `TuiLauncher.launch()`，真实实现先通过 `CodingXTuiModel.startupBanner()` 把 `>_ CodingX CLI (v0.1.0)` 启动卡片和 `Tip: Build faster with CodingX.` 写入普通 shell 输出，再启动 tui4j `Program.run()` 维护后续交互区。该拆分用于规避 Windows 终端下 tui4j 普通屏幕 renderer 按活动区域高度裁剪首帧的问题，确保 logo 和启动卡片不会被裁掉。`CodingXTuiModel.view()` 的活动区只渲染真实 transcript、单行 composer 和底部状态行；初始态不会追加假 transcript，也不会用空白 viewport 把输入行推到窗口底部。`TuiStatusBarRenderer` 只展示后端选模来源、当前工作区、运行状态和 `Plan mode` / `Chat mode` 文案；模型名、MCP 连接数和工具数量没有后端真实字段时不会显示。
10. 用户在 `Textarea` 输入内容时，`CodingXTuiModel.view()` 会把 composer 稳定渲染为单行 `› 输入内容/placeholder`；当输入以 `/` 开头时，模型在输入区上方渲染纯文本命令面板。用户按 Enter 后，模型先判断是否为本地 Slash Command：`/login` 调用 `CliAuthService.loginWithBrowser()` 打开浏览器登录，`/logout` 调用 `CliAuthService.logout()` 清理本机 token，`/help` 或 `/` 只展示命令列表；未知命令会展示中文错误和可用命令，不会进入后端聊天流。
11. 普通任务提交前，`CodingXTuiModel` 会通过 `CliAuthService.isLoggedIn()` 检查本机配置是否已有 token。若没有 token，TUI 先追加中文提示并自动调用浏览器登录；登录成功后继续发送用户原任务，登录失败则把状态置为 `error` 并保留中文兜底提示，避免用户只看到泛化的请求失败。认证服务缺失的测试或旧嵌入链路仍可直接提交任务，由后端事件源做最后的未登录守卫。
12. 用户在 `Textarea` 输入普通任务后按 Enter，`CodingXTuiModel` 会先于 `textarea.update(...)` 拦截提交键，并兼容 JLine/Windows 终端可能上报的 CR(`enter`) 和 LF(`ctrl+j`) 两种回车类型；空任务直接忽略，非空任务会先追加 `> 用户任务` 到同一份 transcript，再把状态切到 `running` 并重置底部输入框。这个顺序保证回车不会被 Textarea 当成编辑换行吞掉，也不会把用户问题打印到普通 scrollback 后和助手回答分离。若当前轮仍是 `running`，再次按 Enter 只保留输入框内容，不追加第二条问题、不发起第二个后端请求；用户等当前轮完成后再按 Enter，下一条问题才进入 transcript，保证界面按 `问题 -> 回答 -> 下一个问题 -> 下一个回答` 展开。当事件源支持流式推送时，模型把任务文本和当前工作区传给 `BackendChatEventSource`，后端流消费线程通过 tui4j `Program.send(...)` 把每个 `AgentEvent` 送回主更新循环；连续 `ASSISTANT_DELTA` 会更新同一个助手回答块，避免后端小片段在 TUI 中变成散乱项目符号列表。普通屏幕 renderer 保留尾部内容时，如果当前轮回答过长会把最新 `> 用户任务` 顶出可见区，`CodingXTuiModel` 会按 JLine `WCWidth` 和窗口宽度估算视觉行，把最近一次用户问题固定在当前轮输出尾部上方，再裁剪回答尾部，确保多行输出和自动换行的长 Markdown 段落都不会隐藏用户刚提交的问题；交给 tui4j renderer 前，模型会把后端 JSON、长 Markdown 和长路径按终端宽度预换行，并把最终视图换行统一为 LF，让 renderer 的逻辑行数与 Windows Terminal 的视觉行数一致，避免 CRLF 行尾和自动换行造成后续刷新错位。模型还会在输入框上方单独渲染一条当前问题固定区，提交后、流式生成中、完成、错误或中断状态都会保留最近一次用户消息，直到下一次提交覆盖；状态栏会在窄终端下退化为单行摘要，避免长工作区路径换行后挤掉当前问题。
13. `BackendChatEventSource` 每轮请求都会从 `CliConfigStore` 读取最新 `serverUrl`、`token` 和 `lastSessionId`，构造 `GET /api/chat/stream` 请求。请求固定携带 `question`、`runtimeTarget=local` 和当前工作区绝对路径 `repositoryPath`；当 `lastSessionId` 是数值字符串时，额外携带 `conversationId` 续接后端会话；token 非空时写入 `satoken` header。若本机 token 为空，事件源会在发出 `TURN_STARTED` 后直接生成中文未登录错误并跳过 HTTP 请求；若 token 已失效且后端返回 `application/json` 统一错误，CLI 优先把 `ApiResponse.message` 转成错误事件。
14. 后端返回 SSE 后，`SseEventParser` 按空行拆分 `event:` / `data:` 块，`BackendChatEventMapper` 将 `meta`、`message`、`thinking`、`tool-call`、`mcp-call`、`finish`、`reject`、`queued`、`queue-accepted`、`error`、`done` 映射为 CLI 现有 `AgentEvent`。`meta.conversationId` 或 `finish.conversationId` 到达后，事件源会把数值会话 ID 写回用户主目录配置的 `lastSessionId`，下一次 TUI 输入自然延续同一个后端会话。TUI 的 `Plan mode` / `Chat mode` 不再只是本地状态栏文案，提交任务时会通过 `planMode=true/false` 查询参数传给后端聊天流。
15. `TuiTranscriptRenderer` 把非活动流事件渲染成对话流和工具状态行；助手正文由 `CodingXTuiModel` 先合并活动回答块再写入 transcript，思考增量显示为 `Thinking:`，工具事件优先展示后端 `displayName` 或 `toolId`，完成事件显示 `Task completed: COMPLETED`，错误事件显示 `! Error` 并让底部状态变为 `error`。`shift+tab` 切换当前提交模式：Plan mode 下后端会在系统提示中要求模型优先拆解计划、风险和确认点，避免主动执行写入类工具；Chat mode 下按普通执行策略处理。
16. 用户执行 `exec`、`resume` 或 `sessions` 时，命令分发器不会启动任务或独立会话流程，而是返回非零退出码和 TUI-only 中文提示。这样 CLI 保持单一产品形态，后续会话恢复、会话列表和权限审批都应接入 TUI 内部工作流。

## 关键文件

- `cli/pom.xml`：通过 Maven Shade Plugin 在 `package` 阶段生成可执行 `target/codingx.jar`。
- `script/install-codingx.ps1`：Windows 本地安装脚本，负责打包、复制 jar、生成启动器和写入用户 PATH。
- `cli/src/main/java/com/codingx/cli/CodingXCli.java`：CLI 进程入口，负责组装配置读写器和 TUI 启动器。
- `cli/src/main/java/com/codingx/cli/command/CliCommandRunner.java`：命令解析和流程编排，覆盖 `login` / `logout`、`auth login` / `auth logout`、默认 TUI、显式 `tui` 和非交互命令拒绝。
- `cli/src/main/java/com/codingx/cli/auth/CliAuthService.java`：CLI 认证编排服务，负责 loopback 登录、设备码轮询、PKCE challenge、后端 token 兑换、登录态检查、退出登录和配置保存。
- `cli/src/main/java/com/codingx/cli/auth/LoopbackCallbackServer.java`：本机临时回调服务，只监听 `127.0.0.1`，校验 state 后把授权码交还 CLI。
- `cli/src/main/java/com/codingx/cli/auth/SystemBrowserLauncher.java`：系统浏览器打开适配器，生产环境用于跳转用户端 `/cli-login`。
- `cli/src/main/java/com/codingx/cli/tui/TuiLauncher.java`：TUI 启动边界，便于命令层单测替换真实终端入口。
- `cli/src/main/java/com/codingx/cli/tui/CodingXTuiLauncher.java`：tui4j `Program` 启动器，负责先打印固定启动卡片，再在普通屏幕运行交互区并保留 shell 命令上下文。
- `cli/src/main/java/com/codingx/cli/tui/CodingXTuiModel.java`：TUI 状态模型，维护启动 banner、事件窗口、输入框、Slash Command 面板、任务提交前登录检查、活动助手回答块、计划模式和状态栏；当前轮输出过长时按终端宽度固定展示最新用户问题，并在提交后到下一次提交前把最近问题固定在输入框上方。最终视图会先按终端宽度预换行并统一 LF，底部 composer 使用稳定单行渲染，问题、输入框和状态栏保持连续三行，避免 Windows Terminal 自动换行或 tui4j 尾部裁剪造成 renderer 刷新错位。
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
- `backend/src/main/java/com/codingx/auth/interfaces/controller/CliAuthController.java`：CLI 授权 HTTP 协议层，提供授权码、设备码和 token 兑换接口。
- `backend/src/main/java/com/codingx/auth/application/service/CliAuthApplicationService.java`：CLI 授权应用服务，集中处理 loopback 白名单、PKCE 校验、一次性 code 消费、设备码状态和 CLI token 创建。
- `backend/src/main/java/com/codingx/auth/application/service/InMemoryCliAuthStateStore.java`：短期 CLI 授权状态内存存储，按过期时间清理授权码和设备码。
- `frontend/user/src/views/CliLoginView.tsx`：用户端 CLI 授权页，支持 loopback 与设备码两种模式，复用网页登录态并遵循亮暗主题变量。
- `frontend/user/src/api/cliAuthApi.ts`：CLI 授权前端 API 封装，统一解析后端 `ApiResponse.message`。
- `cli/src/test/java/com/codingx/cli/CliDistributionTest.java`：CLI 分发契约测试，验证可执行 jar 配置和 Windows 安装脚本关键行为。

## 关键数据结构

- `AgentEventType`：定义 CLI MVP 当前识别的事件类型，包括 `SESSION_STARTED`、`TURN_STARTED`、`ASSISTANT_DELTA`、`TOOL_STARTED`、`COMMAND_OUTPUT_DELTA`、`APPROVAL_REQUESTED`、`TURN_COMPLETED`、`ERROR` 等。
- `CliConfig`：保存后端地址、satoken、审批策略和最近会话标识；首次运行时默认后端地址为 `http://localhost:5001`，审批策略为 `conservative`。
- `codingx.jar`：Shade 打包后的 CLI 可执行 jar，安装脚本会复制到用户目录 `.codingx/bin/codingx.jar`。
- `codingx.cmd` / `codingx.ps1`：Windows 启动器，负责从 PATH 接收 `codingx` 命令并转发到 `java -jar`。
- `AgentEventSource`：事件源抽象边界；生产实现是 `BackendChatEventSource`，测试可继续使用 `MockAgentEventSource`。
- `StreamingAgentEventSource`：流式事件源扩展；同步 `startTurn` 仍会收集事件列表，流式 `startTurn(..., Consumer<AgentEvent>)` 用于 TUI 实时刷新。
- `SseEvent`：后端 SSE 事件块，包含事件名和原始 data 文本。
- `CliAuthorization`：后端短期授权码状态，包含 code、userId、state、redirectUri、codeChallenge 和过期时间；读取成功后立即消费。
- `CliDeviceAuthorization`：后端设备码状态，包含内部 deviceCode、用户可读 userCode、已授权 userId 和过期时间；pending 时可重复查询，approved 后换取 token 会立即消费。
- `CliAuthorizeRequest` / `CliTokenExchangeRequest`：浏览器授权和 CLI token 兑换请求，配合 PKCE S256 保证 code 只能被原始 CLI 请求兑换。
- `CliDeviceStartResponse` / `CliDeviceTokenResponse`：设备码启动和轮询响应，终端根据 `pollIntervalSeconds` 控制轮询节奏。
- `CodingXTuiModel`：TUI 内存状态，包含 `Viewport` 事件窗口、`Textarea` 任务输入框、当前工作区、活动助手回答块、`Plan mode` / `Chat mode`、运行状态、最近一次用户问题、终端宽高和后端流消费线程；提交任务时会把当前模式传给事件源。UI 状态只在进程内维护，会话续接状态写入用户主目录 CLI 配置。普通屏幕可见区按终端视觉行计算，不只按 `\n` 逻辑行计算，避免单行长回复自动换行后继续隐藏用户问题；当前问题固定区参与视图渲染和高度估算，保证流式增量刷新、完成态、错误态或中断态下用户问题都靠近输入框可见。最终 `view()` 输出会预换行并统一 LF，底部 composer 只渲染一行，保证 tui4j 标准 renderer 的光标回退行数和尾部裁剪都不会吞掉用户消息。
- `TuiTranscriptRenderer`：TUI 展示层事件映射；工具名和工具结果优先来自后端 payload，没有真实后端字段的 MCP 连接状态、工具数量和模型名不会在 TUI 中硬编码展示。

## 测试与验证

- `cd cli && mvn test`
- `cd backend && mvn -Dtest=CliAuthApplicationServiceTest,CliAuthControllerTest test`：覆盖 loopback 白名单、一次性 code、PKCE 校验、设备码 pending/approved 和 Controller 协议。
- `cd cli && mvn -Dtest=CliAuthServiceTest,CliCommandRunnerTest,BackendChatEventSourceTest test`：覆盖浏览器回调、设备码轮询、`auth login` 命令分发和 SSE 401 JSON message 解析。
- `cd frontend/user && npm run test:run -- tests/views/CliLoginView.test.tsx`：覆盖 loopback 授权、设备码授权和参数缺失错误展示。
- `cd cli && mvn -Dtest=CodingXTuiModelTest test`：覆盖用户问题可见性、完成后问题锚定、底部三行尾部裁剪、TTY 单行 composer、LF-only renderer 视图、运行中输入保留、连续问答顺序、长输出裁剪、长单行自动换行、视图预换行，以及真实 `Program.run()` + `program.send` 流式增量时底部可见用户问题的场景。
- `cd cli && mvn package`
- `powershell -ExecutionPolicy Bypass -File script/install-codingx.ps1 -ProjectRoot D:\code\CodingX`
- `where codingx`：验证命令解析到用户目录 `.codingx/bin/codingx.cmd`。
- `codingx exec smoke-test`：验证 PATH 命令进入 CLI 命令分发，并继续拒绝非交互任务模式。
