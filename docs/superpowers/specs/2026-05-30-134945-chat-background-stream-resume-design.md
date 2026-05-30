# Chat Background Stream Resume Design

**Date:** 2026-05-30
**Status:** Approved

## Goal

聊天任务必须在用户离开当前页面、切到新会话或刷新后继续作为后台任务运行；用户重新进入仍在运行的会话时，应能继续接收该任务的实时输出，并最终通过数据库回放收敛到完整结果。

## Root Cause

现有后端会在 `/api/chat/stream` 入口创建 `task`、`chat_execution_run` 与 `task_expert` 绑定，SSE 连接断开本身不会取消后台 `Future`。问题在两个边界：

1. 前端 `startNewConversation` 在切到新建态时会调用取消接口，导致用户“退出当前会话”被当成显式停止任务。
2. 前端重新打开运行中会话时只调用消息/步骤/来源回放接口，没有订阅 `/api/chat/conversations/{conversationId}/stream`，所以看不到正在进行的后续输出。
3. 后端 `ChatSseRegistry` 只向当前已连接 emitter 发布事件，没有为运行中的会话保留断线期间的事件，重新订阅只能收到重连后的新事件。

## Design

后端保留 `task` 与 `task_expert` 表。`task` 是后台任务权威状态，供会话列表恢复运行中与完成提醒；`task_expert` 当前仍是专家选择回放的兼容数据源，不能在本次删除。

`ChatSseRegistry` 增加运行期内存事件缓冲。每次 `publish` 先把事件追加到对应会话的有限缓冲，再尝试发送给当前连接；没有连接时也要保留事件。新订阅注册后立即回放缓冲，再接收后续实时事件。任务进入终态并调用 `complete(conversationId)` 时，关闭连接并清理该会话缓冲，长期历史仍以数据库回放为准。

前端在打开运行中会话后，如果当前没有活跃流订阅，就创建一个运行中的助手占位消息并订阅 `/api/chat/conversations/{conversationId}/stream`。订阅消费复用现有 SSE 事件归一化逻辑，收到 `finish` 后立刻解除发送锁，再通过现有回放接口补齐步骤、来源、产物和最终落库消息。

`startNewConversation` 只清空前端当前视图与本地 SSE 连接，不再调用取消接口。显式点击“停止生成”仍调用 `/api/chat/conversations/{conversationId}/cancel`，保持用户主动取消能力。

## Error Handling

重连流请求未授权时触发现有未登录回调。重连失败只影响实时显示，数据库任务继续运行；前端保留会话侧栏运行状态，后续会话列表刷新仍可恢复终态。后端发送到断开的 emitter 时只移除连接，不能把客户端断开升级为任务失败。

## Testing

新增后端单测覆盖：无连接发布也会缓冲、注册时回放缓冲、完成后清理缓冲。新增前端 Hook 测试覆盖：打开运行中会话会订阅会话 SSE 并继续拼接输出；切到新建会话不会调用取消接口；显式取消仍会调用取消接口。
