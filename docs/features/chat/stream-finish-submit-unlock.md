# 聊天流式完成收尾与发送解锁

## 功能用途

聊天流式回答收到 `finish` 事件后，输入区应立即恢复可发送状态；即使后续历史回放接口仍在等待，用户也可以继续发送下一条消息。刷新页面时，本地快照应恢复已完成回答，而不是停留在“正在生成回答...”。

## 使用入口

- 用户前端聊天页：输入框发送按钮、回车发送。
- 前端流式消费：`useChatWorkspace` 的 SSE `finish` 事件处理。

## 核心流程

1. `submitMessage` 创建带编号的提交锁，避免双击或回车重复触发同一条请求。
2. SSE `finish` 到达后，前端用真实会话 ID、助手消息 ID 和最终正文替换乐观助手消息。
3. 最终消息立即写入当前工作区快照，保证刷新恢复不会读取到仍处于 `streaming` 的乐观消息。
4. 当前流会话编号进入已完成集合，允许原请求继续执行会话列表和历史明细回放。
5. 当前提交锁和流控制器只释放本次流，避免旧请求 finally 误清理后续新请求的状态。

## 关键文件

- `frontend/user/src/views/chat/useChatWorkspace.ts`：提交锁、SSE finish 收敛、本地快照持久化和消息引用同步。
- `frontend/user/tests/views/chat/useChatWorkspace.submit.test.ts`：finish 后立即继续发送与快照状态回归测试。

## 边界条件

- 只有 `finish` 已到达才提前释放发送入口；未完成、排队、取消和异常仍按原流控处理。
- 后续慢回放只负责补齐步骤、引用、产物和当前绑定信息，不再占用发送按钮。
- 数组式消息写入会同步刷新 `messagesRef`，避免极快 SSE finish 读到旧闭包后把已替换消息带回。

## 验证方式

- 前端定向测试：`npm run test:run -- tests/views/chat/useChatWorkspace.submit.test.ts tests/views/chat/useChatWorkspace.test.ts`
- 前端构建：`npm run build`
- 浏览器验证：通过 CDP 打开 `http://localhost:5002`，确认输入内容后发送按钮可用且未显示“停止生成”。
