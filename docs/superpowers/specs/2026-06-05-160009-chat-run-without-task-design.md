# Chat Run Without Task Design

**Date:** 2026-06-05
**Status:** Approved

---

## Background

当前聊天发送链路会同时写入 `chat_message`、`chat_execution_run`、`task` 和 `task_expert`。其中 `chat_message` 保存完整用户消息与助手消息，`chat_execution_run` 保存一次消息执行的运行状态，`task` 又保存同一次运行的任务投影，`task_expert` 保存专家选择兼容绑定。这导致每条聊天消息都会额外生成任务记录，运行状态和专家上下文存在多处事实源，后续维护需要同时处理重复数据。

本设计将聊天后台执行从任务模块中剥离：聊天运行状态只由 `chat_execution_run` 承担，聊天消息只由 `chat_message` 承担，专家/技能/MCP 选择只由 `chat_execution_step` 的上下文步骤承担。`task` 与 `task_expert` 表及其后端模块不再参与聊天链路，并从基线结构与迁移脚本中删除。

## Goals

- 发送云端聊天消息时不再创建 `task` 行。
- 删除 `public.task` 与 `public.task_expert` 表，并清理后端 `task` 模块。
- `chat_execution_run` 成为聊天后台运行状态的唯一权威来源。
- `chat_execution_step.metadata_json` 中的 `expertCode` 成为专家选择回放的唯一权威来源。
- 保留前端现有 `activeTaskId/activeTaskStatus` 响应字段作为兼容字段，但字段值必须来自 `chat_execution_run`，不能再查询 `task` 表。
- 聊天后台执行、刷新续流、完成提醒、取消与失败收口能力保持可用。

## Non-Goals

- 不在本次重命名前端所有 `Task` 命名和 `/tasks` 管理端路由；管理端当前页面实际是会话管理，路由改名可后续单独整理。
- 不删除 `expert` 表；专家配置仍然保留。
- 不重构 `chat_execution_step` 的整体结构，只复用现有上下文步骤保存专家选择。

## Architecture

聊天运行链路改为 `run` 中心模型。`ChatStreamRequestApplicationService` 继续生成一个长整型运行标识并下发给前端；该标识语义改为 `runId`，短期兼容 payload 中的 `taskId` 字段。`ChatStreamExecutionService` 使用该标识直接写入 `chat_execution_run(status=RUNNING, queue_status=WAITING)`，注册取消句柄并提交后台线程，不再创建 `Task` 聚合，也不写 `task_expert`。

后台线程进入 `ChatApplicationService.sendMessage` 后，用户消息写入 `chat_message` 并绑定 `run_id`。应用服务已经通过 `saveRunCapabilityContext` 写入 `chat_execution_step` 上下文步骤，其中包含 `skillCodes`、`mcpCodes` 和 `expertCode`。重新生成、当前专家回放和历史能力回放统一读取该上下文步骤；旧的 `ChatExpertRepository.findByTaskId/bindTaskExpert` 被移除。

运行收口只更新 `chat_execution_run`。成功路径由 `recordExecutionOutcome` 写入 `COMPLETED` 与响应消息 ID；派发层捕获门控拒绝或异常时写入 `REJECTED` 或 `ERROR`。无论成功、失败或拒绝，派发层都在后台线程结束时把 `chat_conversation.task_completion_read` 标记为未读，前端列表仍可显示任务完成提醒。

## Data Model

- `chat_message`：唯一消息事实源，保存用户/助手正文、thinking、模型、状态和错误。
- `chat_execution_run`：唯一聊天运行状态源，保存 run 状态、队列状态、请求消息、响应消息、错误和开始结束时间。
- `chat_execution_step`：运行上下文源，保存技能、MCP、专家选择及中间过程事件。
- `chat_conversation.last_run_id`：最近一次聊天运行 ID；刷新、续流和列表投影都通过它查找最新 run。
- `task`：删除。
- `task_expert`：删除。

为兼容历史数据，迁移脚本先将仍存在于 `task_expert` 的专家绑定补写到对应 run 的 `chat_execution_step` 上下文中，再删除 `task_expert`。`chat_execution_run.task_id` 字段在本次保留为兼容列，值继续等于 `id`；删除该列会牵涉更多历史查询和管理端 trace 展示，后续可单独清理命名。

## API Behavior

`/api/chat/stream` 的 meta payload 短期同时保留 `taskId` 和新增 `runId`。前端现有代码继续使用 `taskId` 不会中断，新代码可逐步切换到 `runId`。

`/api/chat/conversations` 继续返回 `activeTaskId`、`activeTaskStatus`、`lastTaskId`、`lastTaskStatus` 和 `lastTaskFinishedAt`。这些字段只是兼容命名，值必须直接从 `chat_execution_run` 折算：`RUNNING/WAITING/ACQUIRED` 输出运行中，`COMPLETED/SUCCESS` 输出 `SUCCEEDED`，`ERROR/FAILED/REJECTED/CANCELLED` 输出 `FAILED` 或对应终态。

`/api/tasks` 与 `/api/tasks/{id}/stream` 被删除。管理端会话管理页面已经使用 admin chat API，不依赖这些接口。

## Error Handling

后台线程中的业务冲突、已知运行异常和未知异常仍由派发层兜底。派发层必须保证异常路径写入 `chat_execution_run` 终态，发布前端错误事件，并释放会话运行锁。任务表删除后，异常路径不能再依赖 `Task.fail` 或 `TaskRepository.save`。

如果会话不存在或提醒状态更新失败，只记录 warn 日志，不反向覆盖已经完成的 run 终态。

## Testing

后端按 TDD 增加或调整单元测试：

- `ChatStreamExecutionServiceTest` 验证云端聊天只写 `chat_execution_run`，不再依赖 `TaskRepository` 或 `ChatExpertRepository.bindTaskExpert`。
- `ChatConversationViewServiceTest` 验证会话列表运行状态只由 `chat_execution_run` 投影。
- `ChatWorkspaceQueryServiceTest` 验证当前专家从 `chat_execution_step` 上下文回放。
- `ChatRuntimePersistenceStructureTest` 或等价数据库结构测试验证 `schema.sql` 不再包含 `task` 与 `task_expert` 表。

完成后按后端改动执行 `mvn compile` 与 `mvn test`。
