# Chat Real Goal Mode Design

## Goal

把聊天目标模式从“前端开关 + executionSteps 推导进度”升级为真正的会话级目标系统。目标由模型通过 `get_goal`、`create_goal`、`update_goal` 工具显式创建和跟进，后端数据库保存权威状态，右侧悬浮窗只展示当前会话的 active goal，不再把普通聊天流式输出或工具步骤误判为目标进度。

## Reference Findings

Claude Code 没有独立的 goal mode。它的 Plan Mode 是权限与审批模式，计划正文写到 `~/.claude/plans` 的 Markdown 文件；旧 Todo 只存在 AppState 内存，并靠 transcript 中最后一次 `TodoWrite` 恢复；新版 Task 是 `~/.claude/tasks/<taskListId>/<taskId>.json` 文件实体，并通过文件监听驱动 UI。这个模型适合本地 CLI，但 Web 产品不应直接照搬本地 JSON 文件目录。

Codex 把三类概念拆得更清楚：Goal 是 thread 级长期目标，Plan Mode 是协作/审批模板，`update_plan`/TodoList 是 turn 级短期执行清单。Codex 本地会话以 JSONL rollout 为事实日志，SQLite 作为线程列表和状态查询索引；云任务则通过后端 task API 读取。CodingX 应借鉴这种“目标、计划模式、执行进度分离”的边界。

## Current Problem

当前 CodingX 的桌面目标模式只做了三件事：前端保存 `goalModeEnabled` 布尔值、发送 SSE 时追加 `planMode=true`、右侧 `GoalProgressPanel` 从 `executionSteps` 统计完成比例。这个设计会产生两个问题：第一，目标不是后端权威实体，刷新或切换会话后无法可靠恢复；第二，executionSteps 是工具过程证据，不是目标状态，普通生成、工具调用或计划步骤都可能被 UI 包装成“目标进度”。

后端内置 `get_goal`、`create_goal`、`update_goal` 工具目前只写 `CodexBuiltinChatToolExecutor` 的进程内 `ConcurrentHashMap`。该缓存没有会话隔离、没有数据库持久化、服务重启丢失，也无法被前端按 conversation 查询。因此它不能承载用户要求的“真正的目标”。

## Architecture

新增三张数据库表：

- `chat_goal` 保存会话级目标本体。一个会话同一时间只允许一个 active 目标。
- `chat_goal_step` 保存目标下的步骤快照。步骤状态来自模型工具调用，不从 executionSteps 推导。
- `chat_goal_event` 保存目标事件流水。每次创建、更新、完成、阻塞都追加事件，供审计、恢复和调试。

新增 `ChatGoalService` 作为应用服务，集中处理工具调用与页面查询。`CodexBuiltinChatToolExecutor` 不再维护内存目标，而是从 `ChatToolExecutionContext.currentGovernanceContext()` 读取当前 `conversationId/userId/runId`，调用 `ChatGoalService` 完成 `get/create/update`。目标更新成功后，服务通过 `ChatStreamPublisher.publishGoal(...)` 发布 `goal` SSE 事件。

前端新增 `activeGoal` 状态和 `ChatApi.getActiveGoal(...)`。进入或切换会话时查询当前 active goal；SSE 收到 `goal` 事件时更新 active goal。`ChatView` 的右侧悬浮窗只在 `activeGoal != null` 时展示。`goalModeEnabled` 仍然只是输入区发送开关，负责让本轮请求进入目标语境，但它本身不再触发目标窗。

## Data Model

`chat_goal` 字段：

- `id`：目标主键。
- `conversation_id`：所属会话 ID。
- `user_id`：目标归属用户 ID，用于权限校验和查询过滤。
- `goal_key`：模型传入的稳定目标键，缺省为 `default`。
- `title`：目标标题。
- `description`：目标说明，可为空。
- `status`：`ACTIVE`、`COMPLETED`、`BLOCKED`、`CANCELLED`。
- `progress_summary`：模型更新的当前进度摘要，可为空。
- `created_run_id` / `updated_run_id`：创建和最近更新目标的运行 ID。
- `created_at` / `updated_at` / `completed_at` / `deleted`：标准审计字段。

`chat_goal_step` 字段：

- `id`：步骤主键。
- `goal_id`：所属目标 ID。
- `step_key`：模型传入或后端生成的步骤键。
- `title`：步骤标题。
- `status`：`PENDING`、`IN_PROGRESS`、`COMPLETED`、`BLOCKED`、`CANCELLED`。
- `sort_no`：展示顺序。
- `detail`：步骤说明或阻塞原因，可为空。
- `started_at` / `completed_at` / `updated_at` / `deleted`：步骤审计字段。

`chat_goal_event` 字段：

- `id`：事件主键。
- `goal_id`：所属目标 ID。
- `conversation_id`：冗余会话 ID，便于按会话查询审计。
- `run_id`：触发事件的运行 ID。
- `event_type`：`GOAL_CREATED`、`GOAL_UPDATED`、`STEP_UPDATED`、`GOAL_COMPLETED`、`GOAL_BLOCKED` 等。
- `payload_json`：工具输入与更新结果快照。
- `created_at`：事件发生时间。

## Tool Contract

`get_goal` 支持 `goalId` 或 `goalKey`，缺省读取当前会话 active goal。当前会话没有目标时返回“目标不存在”的业务错误，模型应据此决定是否创建目标。

`create_goal` 支持 `goalId/goalKey`、`title`、`description`、`steps`。创建前后端会检查同一会话是否已有 active goal；已有 active goal 时返回该目标，不重复创建。步骤数组中的每一项可包含 `id/key`、`title/content`、`status`、`detail`。

`update_goal` 支持更新 `title`、`description`、`status`、`progressSummary`、`steps`。如果 `status` 进入 `COMPLETED`、`BLOCKED` 或 `CANCELLED`，目标主表写入终态；前端仍能通过 SSE 显示最终状态，但刷新后默认只查询 active goal，已完成目标不再常驻右侧悬浮窗。

## Frontend UX

聊天输入区继续保留“目标模式”按钮。开启按钮只表示后续请求带 `planMode=true`，不会直接创建目标，也不会直接显示右侧浮窗。这样用户没有创建目标时，不会出现“没有目标却显示进度”的错误。

右侧悬浮窗展示 active goal 的标题、状态、完成数量、步骤列表和最近进度摘要。没有步骤时展示“等待模型拆解目标”，但前提仍是后端已经存在 active goal。浮窗使用现有主题令牌，保持工具型、低干扰布局；暗色/亮色模式下背景必须非透明，文本与边框可辨识。

## Error Handling

目标工具缺少 `conversationId` 时返回中文业务错误，避免管理端工具探测或脱离聊天流的调用污染全局目标。目标查询时会校验 `userId` 和 `conversationId`，防止跨用户、跨会话读取。数据库写入失败时通过全局异常处理器返回 `ApiResponse`，SSE 侧发布错误事件由既有聊天链路收口。

前端 `ChatApi` 继续使用统一 `ApiResponseParser`。目标查询失败如果是 404 或业务目标不存在，不应弹出全局错误，而是清空 `activeGoal`；其它接口错误仍按现有 `streamError` 或统一错误展示处理。

## Testing

后端测试覆盖数据库结构、`ChatGoalService` 创建/更新/查询、`CodexBuiltinChatToolExecutor` 工具链路、目标 SSE 发布和会话权限边界。前端测试覆盖 active goal API 归一化、会话切换加载目标、SSE goal 事件更新浮窗、没有 active goal 时普通生成不显示浮窗。

浏览器验证覆盖目标模式按钮、目标创建后右侧浮窗、普通聊天无 active goal 时不显示浮窗，以及亮色/暗色主题下浮窗背景非透明、文字可读。
