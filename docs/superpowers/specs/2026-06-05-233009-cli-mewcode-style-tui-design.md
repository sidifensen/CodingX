# MewCode 风格 CLI TUI 设计规格

## 背景

用户希望 CodingX CLI 不再停留在普通命令输出，而是做成截图中 MewCode 那类全屏 TUI：顶部有品牌、模型、工作区和工具连接状态，中间是对话流与工具状态，底部固定输入框和模式栏。当前 CLI 已经完成 TUI-only 入口，`codingx` / `codingx tui` 会启动 tui4j 界面，`exec` / `resume` / `sessions` 已拒绝为非交互产品模式；本规格只定义下一步外观和交互骨架，不接入真实模型、MCP 执行或数据库持久化。

## 目标

- 把当前 TUI 从“简单 header + viewport + textarea + 状态”升级为接近截图的产品界面。
- 任务提交仍只发生在 TUI 输入框中，保持 CLI 单一产品形态。
- 中间区域展示类似对话时间线：用户消息、助手消息、工具搜索、综合中、命令输出和完成状态都用不同前缀或状态行表达。
- 底部展示固定输入区和状态栏，状态栏包含计划模式和当前模型。
- 保持 Java + tui4j 技术栈，先用 mock 事件源验证体验。

## 非目标

- 不实现真实 LLM 调用。
- 不实现真实 MCP server 连接、工具执行或权限审批。
- 不实现会话列表、会话恢复和持久化。
- 不重新开放 `codingx exec "<任务>"`。
- 不改动后端、用户前端或管理端。

## 推荐方案

采用“模型状态 + 分区 renderer”的方案。`CodingXTuiModel` 继续作为 tui4j `Model`，但将界面文案和布局拆成 focused renderer：顶部 header、transcript、输入栏、状态栏。这样第一阶段可以稳定产出截图风格界面，同时为后续真实事件流和更多交互状态留下边界。

相比把所有字符串继续堆在 `CodingXTuiModel`，拆分 renderer 更容易测试，也能避免模型类快速膨胀。相比重新引入专门 TUI 框架或改语言栈，继续使用 tui4j 对现有 Java CLI 改动最小。

## 界面结构

### Header

顶部区域固定展示：

- ASCII 品牌标记，使用 `CodingX` 和简短符号，不使用复杂 Unicode 画图，避免 Windows 终端宽度不一致。
- 版本号，初始显示 `CodingX v0.1.0`。
- 当前模型，初始显示 `GLM-5.1`。
- 当前工作区路径。
- 工具连接状态，初始显示 `Connected to 1 MCP server(s), 2 tools registered`，当前只是 mock 文案。

### Transcript

中间滚动区展示消息和事件：

- 用户任务显示为 `> 用户输入`。
- 助手正文显示为带圆点或缩进的多行文本，模拟截图中的回答块。
- 工具开始和完成显示为状态行，例如 `✓ ToolSearch (0.0s)`。
- 正在运行的综合阶段显示为 `Synthesis... (7s)` 或 `Synthesizing...`。
- 命令输出仍可复用 `TerminalRenderer` 的语义，但 TUI transcript 应把它转成更简洁的工具输出行，避免像日志一样刷屏。
- 错误事件显示为 `! Error ...`，同时底部状态变成 `error`。

### Input

底部输入区固定显示：

- 输入框 prompt 为 `> `。
- placeholder 为 `Send a message...`。
- Enter 提交当前输入并清空输入框。
- 空白输入不追加 transcript，不改变状态。
- Esc / Ctrl+C 退出。

### Status Bar

底部状态栏显示：

- 左侧模式：`Plan on (shift+tab to cycle)`。
- 右侧模型：`GLM-5.1`。
- 当任务运行、完成或错误时，中间或左侧状态可以追加 `ready` / `running` / `completed` / `error`。
- `shift+tab` 第一阶段只允许在 `Plan on` 和 `Plan off` 之间切换本地状态，不触发真实规划策略。

## 数据与状态

新增或调整的 TUI 内存状态：

- `workspace`：当前启动目录。
- `modelName`：显示用模型名，初始 `GLM-5.1`。
- `planMode`：布尔值，初始 `true`。
- `status`：`ready`、`running`、`completed`、`error`。
- `timelineLines` 或后续更结构化的 transcript item 列表：记录用户消息、助手消息和工具事件。

当前不新增数据库表、不新增缓存、不写后端状态。

## 事件流

1. 用户运行 `codingx` 后进入 TUI；初始化 header、工具连接 mock 文案、欢迎消息、底部输入区和状态栏。
2. 用户输入任务并按 Enter；模型 trim 输入，空白则忽略，非空则追加用户消息并设置 `status=running`。
3. 模型调用当前 `AgentEventSource.startTurn(task, workspace)`；mock 事件源返回一轮有序 `AgentEvent`。
4. TUI renderer 把事件转换成截图风格 transcript 行；工具事件显示为工具状态，助手正文显示为对话内容，错误事件更新状态。
5. 渲染完成后状态变为 `completed` 或 `error`，输入框保持可继续输入。

## 测试策略

- 单测不启动真实 tui4j `Program`，直接构造 `CodingXTuiModel` 并断言 `view()` 文本。
- 为 header、输入区、状态栏、计划模式切换、任务提交 transcript 各写明确断言。
- 保留 `CliCommandRunnerTest` 中 TUI-only 行为，确保 `exec` 不会重新成为任务入口。
- 运行 `cd cli && mvn test`。
- 使用 `mvn -q exec:java "-Dexec.mainClass=com.codingx.cli.CodingXCli" "-Dexec.args=exec 分析这个项目"` 验证 `exec` 仍返回 TUI-only 拒绝提示。

## 文档更新

实现完成后更新 `docs/features/agent/java-cli-terminal-mvp.md`，把当前真实实现从“基础 TUI 骨架”调整为“截图风格 TUI 外壳”，并记录 mock 限制、入口、核心流程和验证命令。

## 风险与约束

- tui4j 的组件渲染依赖终端信息，单测直接渲染时必须提供 `TerminalInfo`，否则可能出现空指针。
- Windows 终端和不同字体对宽字符支持不一致，第一阶段避免复杂 Unicode ASCII 图。
- 不要为了模拟截图而重新引入非交互命令输出；所有任务交互仍必须留在 TUI。
- 当前只是 mock 产品壳，功能文案必须明确没有真实模型、MCP 和持久化能力。
