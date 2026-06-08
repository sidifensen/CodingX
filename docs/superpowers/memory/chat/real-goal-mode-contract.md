---
type: contract
title: chat-real-goal-mode-contract
summary: 约束聊天目标模式的数据库权威状态、目标工具契约和前端浮窗展示来源
tags:
  - chat
  - goal-mode
  - tool-runtime
owned_paths:
  - backend/src/main/java/com/codingx/chat/application/service/goal/**
  - backend/src/main/java/com/codingx/tool/application/service/CodexBuiltinChatToolExecutor.java
  - frontend/user/src/views/chat/useChatWorkspace.ts
  - frontend/user/src/views/ChatView.tsx
related_docs:
  - docs/features/chat/desktop-goal-mode.md
  - docs/superpowers/specs/2026-06-09-chat-real-goal-mode-design.md
entrypoints:
  - backend/src/main/java/com/codingx/chat/interfaces/controller/ChatGoalController.java
  - backend/src/main/java/com/codingx/chat/application/service/goal/ChatGoalService.java
  - frontend/user/src/views/chat/chatApi.ts
last_verified_commit: pending-delivery-commit
status: active
---

# Scope

该契约限定 CodingX 聊天目标模式的状态来源、工具读写和前端展示规则。目标模式不是普通执行步骤的别名，不能从 `executionSteps`、前端状态或输入区开关推导出目标进度。

# Producers and Consumers

- Producer:
  - `CodexBuiltinChatToolExecutor`
  - `ChatGoalService`
  - `ChatGoalRepositoryImpl`
  - `SseChatStreamPublisher`
- Consumer:
  - `ChatGoalController`
  - `ChatApi.getActiveGoal`
  - `useChatWorkspace`
  - `GoalProgressPanel`

# State Rules

- `chat_goal` 是会话级目标本体，按 `conversation_id` 和 `user_id` 归属隔离。
- `chat_goal_step` 是当前目标步骤快照，`update_goal` 传入 steps 时以新快照替换旧快照。
- `chat_goal_event` 是追加流水，记录创建、更新、完成、阻塞和取消等关键事件。
- 同一用户同一会话最多只能有一个 `ACTIVE` 目标，数据库使用 partial unique index 约束。
- `get_goal` 不带 `goalId` 和 `goalKey` 时读取当前会话 active goal，不再隐式改写为 `goalKey=default`。
- 终态目标继续保留在数据库和事件流水里，但 active 查询接口不再返回给右侧浮窗。

# Invariants

- 目标工具必须在聊天会话治理上下文中执行，缺少 `userId` 或 `conversationId` 时返回中文业务错误。
- 目标工具调用服务层时参数顺序保持为 `conversationId, userId, runId`；线程上下文绑定顺序保持为 `userId, conversationId, runId`。
- 目标视图中的 Long ID 必须转成字符串后进入工具 metadata、SSE 事件和 REST 响应，避免前端精度丢失。
- 前端右侧目标浮窗只消费后端 active goal 或 `goal` SSE 事件，不允许由 `planMode` 开关或执行步骤自行制造占位目标。
- 目标模式开关只负责让后续流请求携带 `planMode=true`，不代表目标已经存在。

# Compatibility Notes

- `goalKey` 默认为 `default` 仅用于创建和显式 key 查询；读取当前目标时应优先使用 active goal。
- 前端刷新或切换会话时必须先清空旧 `activeGoal`，再读取当前会话 active goal，避免串会话展示。
- `SseChatStreamPublisher` 只发布 `goal` 事件名和目标快照，不在 SSE 层改写目标状态。
