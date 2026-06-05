# CLI TUI Backend Chat Stream Design

## 背景

当前 `codingx` 已经进入截图风格 TUI，但 TUI 输入仍由 `MockAgentEventSource` 返回本地假事件。用户要求的产品形态是 Claude Code / Codex CLI 风格：输入一个命令进入全屏终端模式，随后在终端内输入任务，并对接现有 Web 对话能力，而不是保留 `codingx exec "任务"` 这种非交互入口。

本次只改 CLI 侧，不重设计后端 Controller，不新增数据库表。后端已存在 `GET /api/chat/stream` SSE 接口，并支持 `runtimeTarget=local` 与 `repositoryPath` 绑定当前仓库工作空间。

## 目标

- `codingx` 和 `codingx tui` 继续启动 TUI，`exec`、`resume`、`sessions` 继续返回 TUI-only 拒绝提示。
- TUI 输入框提交任务后，CLI 读取用户主目录 `~/.codingx/cli.yml` 的 `serverUrl` 和 `token`，携带 `satoken` 请求后端 `/api/chat/stream`。
- CLI 请求必须携带 `question`、`runtimeTarget=local`、`repositoryPath=<当前工作区绝对路径>`；当配置中的 `lastSessionId` 是数值时，还要携带 `conversationId` 以续接现有会话。
- CLI 消费后端 SSE 事件并映射为现有 `AgentEvent`，让 TUI 复用既有 transcript 渲染边界。
- 后端 `meta.conversationId` 或 `finish.conversationId` 到达后，CLI 将其写回 `lastSessionId`，下一轮 TUI 输入自动复用同一后端会话。
- TUI 需要支持后台流式事件推送，避免一次请求结束后才整体刷新。

## 非目标

- 不新增后端接口、Controller、数据库表或迁移脚本。
- 不引入独立桌面端，也不恢复非交互任务命令。
- 不在本次实现 TUI 内会话列表、会话选择、取消运行或权限审批完整交互。
- 不把 MCP 状态、模型名称和工具统计做成真实遥测；本次仅让聊天流真实接入。

## 架构

新增 CLI 后端聊天流客户端，作为 `AgentEventSource` 的真实实现。同步边界仍保留，便于测试和旧 mock 复用；同时新增一个流式扩展接口，让 TUI 可以注册事件回调并通过 tui4j `Program.send(Message)` 把后台线程收到的事件送回模型更新。

主要流转如下：

1. `CodingXCli.main` 创建 `CliConfigStore`，并将其传给新的后端事件源，再把事件源注入 `CodingXTuiLauncher`。
2. `CodingXTuiModel.submitTask` 清洗用户输入，追加用户行并切换到 `running`；如果事件源支持流式扩展，则后台线程消费 SSE，每个事件通过 `Program.send(...)` 回到 TUI 模型。
3. 后端事件源从 `CliConfigStore.load()` 读取最新配置，构造 `/api/chat/stream` 请求；token 非空时写入 `satoken` header，工作区路径通过 `Path.toAbsolutePath().normalize()` 传入 `repositoryPath`。
4. SSE 解析器按标准空行分隔事件块，解析 `event:` 与 `data:`；payload 使用 Hutool JSON 解析为 `Map`，无法解析时保留原始文本。
5. 事件映射器把 `message`、`thinking`、`tool-call`、`mcp-call`、`step`、`reference`、`artifact`、`finish`、`reject`、`queued`、`queue-accepted`、`error`、`done` 映射为 `AgentEvent`。
6. `meta` 或 `finish` 携带数值会话 ID 时，事件源保存新的 `CliConfig(lastSessionId=...)`；保存失败作为错误事件返回，不吞掉可见问题。

## 错误处理

- 非 2xx 响应不进入 SSE 解析；客户端读取响应体，优先提取后端 `ApiResponse.message`，失败时使用中文兜底提示。
- 请求构造失败、网络失败、SSE 解析异常统一转为 `AgentEventType.ERROR`，TUI 底部状态切到 `error`。
- token 为空时仍发起请求，让后端返回统一登录错误；CLI 不自行改写后端错误语义。
- 只有数值型 `lastSessionId` 会作为 `conversationId` 发送，避免本地临时 ID 触发后端 Long 参数错误。

## 测试策略

- 使用 JUnit 和 JDK `HttpServer` 模拟后端 SSE，验证请求参数、`satoken` header、SSE 事件映射和会话 ID 回写。
- 使用 TUI 模型单测验证异步事件消息可以实时追加 transcript，并根据终态事件更新 `completed/error`。
- 使用现有命令分发测试确保 `codingx exec` 仍被拒绝。
- 最终运行 `cd cli && mvn test`，并用带 `exec` 参数的 Maven 启动命令验证非交互模式仍退出。
