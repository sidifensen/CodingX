# 聊天后台运行续流

## 功能用途

聊天运行在用户离开当前页面、切到新建会话或刷新后仍继续作为后端后台执行。用户重新打开仍在运行的会话时，前端会重新订阅会话 SSE，继续接收后台输出，并在运行结束后通过历史回放收敛最终消息、步骤、来源与产物。

## 使用入口

- 用户前端聊天页：发送消息、刷新页面、切到新建会话、重新打开运行中的历史会话。
- 后端 SSE 入口：`/api/chat/stream`、`/api/chat/conversations/{conversationId}/stream`。

## 核心流程

1. `/api/chat/stream` 创建 `chat_execution_run`，并把 `runId` 及兼容字段 `taskId` 通过 `meta` 事件下发给前端。
2. `ChatSseRegistry` 在聊天运行期间按会话缓存有限数量的 SSE 事件；没有浏览器连接时也保留事件。
3. 前端收到 `meta.runId` 或兼容 `meta.taskId` 后立即把本地会话标记为 `activeTaskStatus=RUNNING`，并把乐观用户消息和流式助手消息迁移到真实 `conversationId` 快照，避免刷新后只恢复空会话壳。
4. 用户刷新页面、关闭页面、切到新建会话或切换其他会话时，前端只把当前流会话标记为本地订阅脱离，保存半截输出快照，不调用取消接口，也不展示“已停止当前生成”。同一前端页面内切到其他会话或新建态时，已经拿到真实 `conversationId` 的 `/api/chat/stream` 会迁移到前端后台上下文继续消费；未拿到真实会话 ID 的 `pending-conversation` 仍中断本地订阅，避免没有可持久化目标时串线。
5. 用户显式点击“停止生成”时，前端才把助手消息标记为 `cancelled`、展示“已停止当前生成”，并调用后端取消接口停止后台运行。
6. 用户重新打开 `activeTaskStatus=RUNNING` 的会话时，前端优先恢复真实会话下的本地快照，并先按 `conversationId`、运行目标和工作空间路径查找仍在内存中消费的前端后台流。命中时直接把该流重新绑定到当前主区，后续 `message`、`finish`、`error` 继续由原 `/api/chat/stream` 读循环写入当前会话，不再请求 `/api/chat/conversations/{conversationId}/stream`。只有刷新页面、浏览器内存已丢失、前端后台流不存在或分区不匹配时，才订阅 `/api/chat/conversations/{conversationId}/stream` 回放后端缓存事件。
7. 运行完成后，后端发布 `finish/done`、关闭会话 SSE 并清理运行期缓存；前端收到 `finish` 后立即把当前会话的 `activeTaskId`、`activeTaskStatus` 从内存态和工作空间快照中清空，再用历史接口补齐最终回放，避免完成后的会话切回时被误判为仍需续流。
8. 刷新或切回会话时，前端会根据会话列表中的 `activeTaskStatus` 判断是否仍需保留本地 `streaming` 助手占位；如果会话已经是终态，只清理空内容的临时助手占位，并把清理后的记录直接写回当前工作空间快照，避免旧快照再次渲染“正在生成回答”。

## 关键文件

- `backend/src/main/java/com/codingx/chat/infrastructure/stream/ChatSseRegistry.java`：会话 SSE 注册、断线清理、运行期事件缓冲与完成清理。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatStreamExecutionService.java`：后台运行创建、运行记录收口、完成提醒状态更新。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：运行中会话续流订阅、本地订阅脱离语义、前端后台流上下文接回、刷新前半截输出快照、SSE 事件消费。
- `frontend/user/tests/views/chat/useChatWorkspace.submit.test.ts`：刷新不误报停止生成、新建不取消后台任务、同页切回接回前端后台流、运行中会话后端续流的回归测试。

## 数据结构

- `chat_execution_run`：聊天后台运行权威状态表。会话列表运行态、终态与完成提醒投影都从该表折算，`task_id` 仅作为短期兼容列保留。
- `chat_execution_step`：运行上下文与过程步骤表。隐藏的 `runtime_context` 步骤保存技能、MCP 与专家选择，历史回放不再依赖 `task_expert`。
- `chat_message`：消息正文权威表。最终助手消息、思考内容与错误信息仍通过历史接口回放。

## 边界约束

- 只有用户显式点击“停止生成”才调用取消接口；切换会话、新建会话、刷新页面、页面卸载都不取消后端任务，也不把 `AbortError` 映射成停止提示。
- 新建会话首次收到 `meta.conversationId` 后必须立即保存真实会话 ID 下的流式快照；后续刷新恢复以真实会话快照为准，旧 `pending-conversation` 只作为发送前的短暂乐观状态。
- 前端后台流接回只允许命中同一 `conversationId`、同一运行目标和同一工作空间路径的上下文；接回时必须从后台上下文移除该流会话编号，恢复 `activeStreamSessionId`、`abortController`、消息、步骤、引用、产物和当前能力上下文，避免后续 SSE 事件继续走后台快照分支。
- `meta.runId` 与兼容 `meta.taskId` 写入的 `activeTaskStatus=RUNNING` 只表示聊天运行仍在继续；`finish`、显式取消或权威会话列表不再声明运行态时，前端必须清理该投影，禁止用旧快照继续驱动侧栏转圈或会话续流。
- 非运行中会话恢复本地快照时，只允许删除空内容、临时 ID 的 `streaming` 助手占位；已经有正文、思考内容或过程卡片的流式消息不得被静默丢弃，仍需交给真实续流或历史接口收敛。
- 运行期 SSE 缓冲只保存在内存中，运行完成后立即清理；长期历史仍以数据库消息和执行回放接口为准。
- 客户端断开导致的发送异常只清理 emitter，不得把后台运行标记为失败。

## 验证方式

- 后端编译：`mvn compile`
- 后端定向探针：发布无连接事件后注册 emitter，`earlySendAttempts > 0`；`complete` 后再次注册为 `0`
- 前端定向测试：`npm run test:run -- tests/views/chat/useChatWorkspace.submit.test.ts`
- 同页切回前端后台流回归：`npm run test:run -- tests/views/chat/useChatWorkspace.submit.test.ts -t "重新打开由流式 meta 创建的运行中会话时应接回前端后台流"`
- 显式取消回归：`npm run test:run -- tests/views/chat/useChatWorkspace.test.ts -t "应在停止生成后保留部分回答并标记为取消"`
- 重新生成完成态回归：`npm run test:run -- tests/views/chat/useChatWorkspace.test.ts -t "重新生成完成后切换会话再返回不应恢复运行中续流"`
- 终态快照占位清理回归：`npm run test:run -- tests/views/chat/useChatWorkspace.test.ts -t "恢复已完成会话快照时应移除残留的空流式助手占位"`
- 前端构建与全量测试：`npm run build`、`npm run test:run`
- 浏览器验证：通过 CDP 打开用户前端，发送消息后刷新页面，确认不会出现“已停止当前生成”，且运行中会话可继续恢复输出。
