# Acceptance Criteria: Chat Background Stream Resume

**Spec:** `docs/superpowers/specs/2026-05-30-134945-chat-background-stream-resume-design.md`
**Date:** 2026-05-30
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 后端在会话没有任何 SSE 连接时仍保留运行期事件。 | Logic | `ChatSseRegistry.publish(conversationId, "message", payload)` 发生在注册 emitter 之前。 | 随后注册同一会话 emitter 时能收到此前发布的 `message` 事件。 |
| AC-002 | 后端完成会话流后清理运行期事件缓冲。 | Logic | 同一会话已发布至少一个缓冲事件并调用 `complete(conversationId)`。 | 再次注册同一会话 emitter 不会收到旧任务事件。 |
| AC-003 | 打开后端标记为运行中的会话会订阅会话 SSE。 | Logic | 会话列表返回 `activeTaskStatus=RUNNING`，用户打开该会话。 | 前端请求 `/api/chat/conversations/{conversationId}/stream`，并把后续 `message` 事件拼接到运行中助手消息。 |
| AC-004 | 切到新建会话不会取消后台任务。 | Logic | 当前会话存在运行中 SSE 连接。 | 调用 `startNewConversation()` 只中断本地连接，不调用 `/api/chat/conversations/{conversationId}/cancel`。 |
| AC-005 | 用户显式点击停止仍取消后台任务。 | Logic | 当前会话存在运行中 SSE 连接且用户调用取消动作。 | 前端调用 `/api/chat/conversations/{conversationId}/cancel`，并把助手消息标记为取消状态。 |
| AC-006 | 数据库任务表与专家绑定表在本次实现中保留。 | API | 使用 PostgreSQL 查询 `public.task` 与 `public.task_expert`。 | 两张表继续存在，代码仍使用 `task` 做任务状态投影，`task_expert` 做专家选择兼容回放。 |
| AC-007 | 浏览器验证中重新进入运行会话能看到继续输出。 | UI interaction | 后端、用户前端启动；发送一条会持续流式输出的消息后切到新建态，再打开原会话。 | 原会话不进入取消态；页面重新订阅后继续显示输出，最终收敛为完成消息。 |
