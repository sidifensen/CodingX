# CLI Web 会话命令

## 功能用途

CLI 会话命令让终端端直接复用 Web 端的聊天会话历史。用户可以运行 `codingx sessions` 查看当前账号最近会话，再运行 `codingx resume <会话ID>` 选择下一次 TUI 任务要续接的后端会话。

## 使用入口

- `codingx sessions`：读取 `~/.codingx/cli.yml` 中的 `serverUrl` 与 `satoken`，请求 Web 后端最近会话。
- `codingx resume <会话ID>`：把数值型会话 ID 写入 `~/.codingx/cli.yml` 的 `lastSessionId`。
- `codingx` 或 `codingx tui`：后续发送任务时由聊天流客户端携带 `conversationId` 续接会话。

## 核心流程

1. 用户在终端运行 `codingx sessions` 后，`CodingXCli` 创建 `BackendConversationClient` 并注入 `CliCommandRunner`。命令层不会启动 TUI，而是调用会话服务读取最近 20 条会话。
2. `BackendConversationClient` 从用户主目录配置读取 `serverUrl` 和 `token`。如果 token 为空，客户端直接返回中文未登录提示，不访问后端。
3. 客户端请求 `GET /api/chat/conversations?pageSize=20`，并在请求头写入 `satoken`。后端仍沿用 Web 端会话列表接口和 `ApiResponse` 响应结构。
4. 客户端兼容解析 `data` 为数组的旧响应，以及 `data.items` cursor page 的新响应。每条会话会被规整为 `CliConversation`，避免 Long 主键、空值或旧字段直接泄漏到命令层。
5. 命令层把会话列表渲染为终端文本，包含会话 ID、标题、状态、更新时间和工作区类型。空列表时返回 `暂无可恢复会话。`，后端错误时优先展示 `ApiResponse.message`。
6. 用户运行 `codingx resume <会话ID>` 后，命令层先校验 ID 必须是数字。校验通过后，它只更新用户级配置的 `lastSessionId`，保留原有 `serverUrl`、`token` 和审批策略。
7. 下一次用户进入 TUI 并提交任务时，既有 `BackendChatEventSource` 会读取 `lastSessionId`，将其作为 `conversationId` 传给 `/api/chat/stream`，从而和 Web 端同一会话继续对话。

## 关键文件

- `cli/src/main/java/com/codingx/cli/CodingXCli.java`：生产入口，注入后端会话客户端。
- `cli/src/main/java/com/codingx/cli/command/CliCommandRunner.java`：处理 `sessions` 和 `resume` 命令。
- `cli/src/main/java/com/codingx/cli/session/BackendConversationClient.java`：调用 Web 会话接口并解析 `ApiResponse`。
- `cli/src/main/java/com/codingx/cli/session/CliConversation.java`：CLI 会话摘要。
- `cli/src/main/java/com/codingx/cli/session/CliConversationService.java`：命令层依赖的会话服务接口。
- `cli/src/test/java/com/codingx/cli/session/BackendConversationClientTest.java`：后端接口解析测试。
- `cli/src/test/java/com/codingx/cli/command/CliCommandRunnerTest.java`：命令分发测试。

## 验证方式

- `cd cli && mvn -Dtest=BackendConversationClientTest test`
- `cd cli && mvn -Dtest=CliCommandRunnerTest test`
- `cd cli && mvn test`
