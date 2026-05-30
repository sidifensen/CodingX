# 聊天后台任务续流

## 功能用途

聊天任务在用户离开当前页面、切到新建会话或刷新后仍继续作为后端后台任务执行。用户重新打开仍在运行的会话时，前端会重新订阅会话 SSE，继续接收后台输出，并在任务结束后通过历史回放收敛最终消息、步骤、来源与产物。

## 使用入口

- 用户前端聊天页：发送消息、刷新页面、切到新建会话、重新打开运行中的历史会话。
- 后端 SSE 入口：`/api/chat/stream`、`/api/chat/conversations/{conversationId}/stream`。

## 核心流程

1. `/api/chat/stream` 创建 `task`、`chat_execution_run`，并把 `taskId` 通过 `meta` 事件下发给前端。
2. `ChatSseRegistry` 在任务运行期间按会话缓存有限数量的 SSE 事件；没有浏览器连接时也保留事件。
3. 前端收到 `meta.taskId` 后立即把本地会话标记为 `activeTaskStatus=RUNNING`，并把乐观用户消息和流式助手消息迁移到真实 `conversationId` 快照，避免刷新后只恢复空会话壳。
4. 用户刷新页面、关闭页面、切到新建会话或切换其他会话时，前端只把当前流会话标记为本地订阅脱离，保存半截输出快照并中断浏览器 SSE 连接，不调用取消接口，也不展示“已停止当前生成”。
5. 用户显式点击“停止生成”时，前端才把助手消息标记为 `cancelled`、展示“已停止当前生成”，并调用后端取消接口停止后台任务。
6. 用户重新打开 `activeTaskStatus=RUNNING` 的会话时，前端优先恢复真实会话下的本地快照，再订阅 `/api/chat/conversations/{conversationId}/stream` 并回放缓存事件。
7. 任务完成后，后端发布 `finish/done`、关闭会话 SSE 并清理运行期缓存；前端再用历史接口补齐最终回放。

## 关键文件

- `backend/src/main/java/com/codingx/chat/infrastructure/stream/ChatSseRegistry.java`：会话 SSE 注册、断线清理、运行期事件缓冲与完成清理。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatStreamExecutionService.java`：后台任务创建、运行记录收口、任务完成提醒状态更新。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：运行中会话续流订阅、本地订阅脱离语义、刷新前半截输出快照、SSE 事件消费。
- `frontend/user/tests/views/chat/useChatWorkspace.submit.test.ts`：刷新不误报停止生成、新建不取消后台任务、运行中会话续流的回归测试。

## 数据结构

- `task`：后台任务权威状态表。保留用于会话列表运行中状态、任务终态与提醒投影。
- `chat_execution_run`：聊天执行记录表。`task_id` 与 `task.id` 对齐，用于消息、步骤、来源、产物的运行链路回放。
- `task_expert`：专家选择旧兼容绑定表。当前仍被“当前专家”回放逻辑使用，本次不删除。

## 边界约束

- 只有用户显式点击“停止生成”才调用取消接口；切换会话、新建会话、刷新页面、页面卸载都不取消后端任务，也不把 `AbortError` 映射成停止提示。
- 新建会话首次收到 `meta.conversationId` 后必须立即保存真实会话 ID 下的流式快照；后续刷新恢复以真实会话快照为准，旧 `pending-conversation` 只作为发送前的短暂乐观状态。
- 运行期 SSE 缓冲只保存在内存中，任务完成后立即清理；长期历史仍以数据库消息和执行回放接口为准。
- 客户端断开导致的发送异常只清理 emitter，不得把后台任务标记为失败。

## 验证方式

- 后端编译：`mvn compile`
- 后端定向探针：发布无连接事件后注册 emitter，`earlySendAttempts > 0`；`complete` 后再次注册为 `0`
- 前端定向测试：`npm run test:run -- tests/views/chat/useChatWorkspace.submit.test.ts`
- 显式取消回归：`npm run test:run -- tests/views/chat/useChatWorkspace.test.ts -t "应在停止生成后保留部分回答并标记为取消"`
- 前端构建与全量测试：`npm run build`、`npm run test:run`
- 浏览器验证：通过 CDP 打开用户前端，发送消息后刷新页面，确认不会出现“已停止当前生成”，且运行中会话可继续恢复输出。
