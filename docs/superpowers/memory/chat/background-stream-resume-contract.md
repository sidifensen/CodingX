---
type: contract
title: chat-background-stream-resume-contract
summary: 约束聊天后台任务在页面断开后的运行、SSE 缓冲与前端续流恢复规则
tags:
  - chat
  - sse
  - task
owned_paths:
  - backend/src/main/java/com/codingx/chat/infrastructure/stream/ChatSseRegistry.java
  - backend/src/main/java/com/codingx/chat/application/service/chat/ChatStreamExecutionService.java
  - frontend/user/src/views/chat/useChatWorkspace.ts
related_docs:
  - docs/features/chat/background-stream-resume.md
  - docs/superpowers/memory/chat/message-process-timeline-contract.md
entrypoints:
  - frontend/user/src/views/chat/useChatWorkspace.ts
  - backend/src/main/java/com/codingx/chat/infrastructure/stream/ChatSseRegistry.java
last_verified_commit: pending-final-commit
status: active
---

# Scope

该契约限定聊天后台任务在浏览器页面离开、刷新、切到新建会话后如何继续执行，以及用户重新打开运行中会话时如何续接 SSE 输出。

# Producers and consumers

- Producer:
  - `ChatStreamExecutionService.java`
  - `SseChatStreamPublisher.java`
  - `ChatSseRegistry.java`
- Consumer:
  - `useChatWorkspace.ts`
  - `ChatApi.conversations/{conversationId}/stream`

# State rules

- 切换会话、新建会话、刷新页面只代表前端本地 SSE 订阅断开，不代表用户要求停止任务。
- 只有显式停止生成才允许调用会话取消接口。
- `/api/chat/stream` 下发的 `meta.taskId` 必须立即写入本地会话投影，并标记 `activeTaskStatus=RUNNING`。
- 前端打开 `activeTaskStatus=RUNNING` 的会话时，必须订阅 `/api/chat/conversations/{conversationId}/stream`。
- 任务完成后，后端必须清理会话运行期 SSE 缓冲，长期历史只从数据库回放接口恢复。

# Data contract

- `task` 是后台任务权威状态表，不能用前端连接状态替代。
- `chat_execution_run.task_id` 仍与 `task.id` 对齐，用于会话运行链路和任务终态投影。
- `task_expert` 当前仍承载专家选择兼容绑定，`listCurrentExperts` 仍依赖该链路回放当前专家。
- `ChatSseRegistry` 只保存短期内存缓冲，缓冲容量是断线重连窗口，不是历史存储。

# Invariants

- 客户端断开造成的 SSE 发送异常只清理当前 emitter，不得让后台任务失败。
- 注册重连时应先回放已缓冲事件，再加入 live emitter 列表，避免重连瞬间乱序。
- 前端恢复流时要跳过本地已展示的缓冲前缀，避免重复拼接 assistant 正文或 thinking 内容。
- finish/done 后前端应通过历史接口收敛最终消息、步骤、来源、产物和当前能力上下文。

# Verification hooks

- 后端：`ChatSseRegistry` 需要覆盖无连接发布后重连回放、完成后清理、客户端断开只移除 emitter。
- 前端：`useChatWorkspace` 需要覆盖新建不取消后台任务、点回运行中会话续流、刷新恢复运行中快照续流、缓冲前缀去重。
- 浏览器：通过 CDP 验证发送长任务后切到新建态，数据库任务仍运行；点回原会话后产生 `/api/chat/conversations/{id}/stream` 请求并继续输出。
