# 阶段任务清单

更新时间：2026-05-13

## 任务分组

### A. 任务运行时

- [ ] `todo` 建立 `runtime` 执行器接口（owner: self）
  交付结果：定义统一执行器协议，为 `mock / local / cloud` 演进留口
- [ ] `todo` 实现 `mock runtime executor`（owner: self）
  交付结果：任务可从 `CREATED` 流转到 `RUNNING` 再到 `SUCCEEDED/FAILED`

### B. SSE 事件流

- [ ] `todo` 建立任务 SSE 订阅与发布（owner: self）
  交付结果：前端可订阅任务事件流，收到状态变化、步骤、摘要事件
- [ ] `todo` 建立聊天 SSE 订阅与发布（owner: self）
  交付结果：前端可订阅聊天流式响应，收到 AI 增量 token 和完成事件

### C. AI 聊天流式链路

- [ ] `todo` 实现 AI 流式回复（owner: self）
  交付结果：通过 `OkHttp` 调用 AI 提供商流式接口，并将增量内容转发到 SSE
- [ ] `todo` 建立聊天消息落库与会话刷新（owner: self）
  交付结果：用户消息、助手回复、错误状态均可持久化

### D. 工作台演示闭环

- [ ] `todo` 打通任务创建 -> 运行 -> SSE 推送 -> 完成（owner: self）
  交付结果：形成可演示的后端主链路
- [ ] `todo` 输出运行与接入说明（owner: self）
  交付结果：说明如何订阅 SSE、如何触发任务、如何测试 AI 聊天

## 任务状态说明

- `todo`：未开始
- `doing`：进行中
- `done`：已完成
