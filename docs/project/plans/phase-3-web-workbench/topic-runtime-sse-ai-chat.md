# Phase 3 Runtime / SSE / AI Chat 设计

更新时间：2026-05-13

## 一、目标

本阶段要把 `Phase 2` 的静态任务主干推进成可演示闭环：

- 任务可以执行
- 任务状态可以变化
- 前端可以通过 `SSE` 看到事件
- AI 聊天可以流式返回

## 二、统一实时通道策略

统一采用 `SSE`，不引入 `WebSocket`。

原因：

- 当前主要是服务端向客户端单向推送
- 浏览器原生支持简单
- 与任务日志流、AI token 流的模型更一致

## 三、任务事件流设计

订阅接口：

- `GET /api/tasks/{taskId}/stream`

典型事件类型：

- `task-connected`
- `task-status-changed`
- `task-step`
- `task-log`
- `task-summary`
- `task-error`
- `task-completed`

推送方式：

- `SseEmitter` 按 `taskId` 注册
- `runtime`、`task application service` 通过事件发布器向对应 emitter 推送

## 四、AI 聊天流设计

订阅接口：

- `GET /api/chat/conversations/{conversationId}/stream`

发送消息接口：

- `POST /api/chat/conversations/{conversationId}/messages`

典型事件类型：

- `chat-connected`
- `chat-user-message`
- `chat-assistant-delta`
- `chat-assistant-completed`
- `chat-error`

执行方式：

- 用户消息先落库
- 通过 `OkHttp` 调用 AI 提供商的流式接口
- 逐行解析流式响应
- 生成增量 token 事件并通过 `SSE` 推送
- 完成后合并助手消息并落库

## 五、运行时执行器设计

`runtime` 模块统一暴露接口：

- `execute(task)`

当前实现：

- `MockRuntimeExecutor`

行为：

- 将任务置为 `RUNNING`
- 写入若干步骤事件与日志事件
- 写入摘要或失败信息
- 将任务终态置为 `SUCCEEDED` 或 `FAILED`

## 六、错误处理

任务 SSE：

- emitter 发送失败时自动移除会话
- runtime 失败时写入 `task-error` 事件，并更新任务状态

聊天 SSE：

- AI 返回错误时推送 `chat-error`
- 对话消息保存失败时记录失败状态，避免前端无响应

## 七、验证方式

- 使用浏览器或 `curl` 订阅 `SSE`
- 触发任务执行，观察事件是否连续返回
- 发送聊天消息，观察 AI 增量 token 是否逐步返回
