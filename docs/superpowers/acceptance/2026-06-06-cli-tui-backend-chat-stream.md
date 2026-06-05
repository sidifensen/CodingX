# Acceptance Criteria: CLI TUI Backend Chat Stream

**Spec:** `docs/superpowers/specs/2026-06-06-010700-cli-tui-backend-chat-stream-design.md`
**Date:** 2026-06-06
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | TUI 提交任务后，CLI 请求后端 `/api/chat/stream` 而不是 mock 事件源。 | Logic | JUnit 启动本地 `HttpServer` 作为后端，配置 `serverUrl` 指向该服务。 | 服务端收到 `GET /api/chat/stream`，查询参数包含用户问题、`runtimeTarget=local` 和当前仓库绝对路径。 |
| AC-002 | CLI 复用登录配置中的 satoken。 | Logic | `~/.codingx/cli.yml` 测试副本含 `token=test-token`。 | 模拟服务端收到请求 header `satoken: test-token`。 |
| AC-003 | CLI 只在 `lastSessionId` 为数值时续接会话。 | Logic | 配置中 `lastSessionId=12345`。 | 请求查询参数包含 `conversationId=12345`；当配置为非数值字符串时不发送该参数。 |
| AC-004 | CLI 能把后端 SSE `meta/message/thinking/tool-call/finish/error` 映射为 `AgentEvent`。 | Logic | 模拟服务端依次返回这些 SSE 事件。 | 测试读取到 `TURN_STARTED`、`ASSISTANT_DELTA`、`THINKING_DELTA`、`TOOL_STARTED`/`TOOL_COMPLETED`、`TURN_COMPLETED` 或 `ERROR`。 |
| AC-005 | CLI 收到后端会话 ID 后写回用户配置。 | Logic | 模拟服务端返回 `meta` 或 `finish`，payload 含 `conversationId=67890`。 | `CliConfigStore.load().lastSessionId()` 等于 `67890`。 |
| AC-006 | 后端非 2xx 响应时，CLI 优先展示后端 `ApiResponse.message`。 | Logic | 模拟服务端返回 401 JSON，包含 `message=请先登录`。 | 事件列表包含 `AgentEventType.ERROR`，payload message 为 `请先登录`。 |
| AC-007 | TUI 能接收后台流式事件并刷新 transcript。 | Logic | 单测直接向 `CodingXTuiModel.update(...)` 发送事件消息。 | `view()` 中出现助手增量文本，终态后底部状态变为 `completed`；错误事件后状态变为 `error`。 |
| AC-008 | 非交互任务命令仍被拒绝。 | API | 执行 `mvn -q exec:java "-Dexec.mainClass=com.codingx.cli.CodingXCli" "-Dexec.args=exec 分析这个项目"`。 | 进程返回非零退出码，输出包含“只支持 TUI 交互模式”。 |

