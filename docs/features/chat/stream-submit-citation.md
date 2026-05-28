# 聊天流式提交与引用链接

## 功能用途

聊天流式提交需要避免同一问题被快速重复发送，同时保证回答正文里的 `[R1]`、`[R2]`、`[R3]` 在流式阶段收到来源后即可点击，不等待完整历史回放。

## 使用入口

- 用户前端聊天页：输入框发送按钮、回车发送。
- 后端流式入口：`GET /api/chat/stream`。

## 核心流程

1. 前端 `submitMessage` 在读取输入和 token 后立即设置同步提交锁。
2. 工作区绑定、附件上传、乐观用户消息和乐观助手消息创建都在同一提交锁保护范围内执行。
3. 后端 `ConversationQueueGate` 对同一会话未释放的执行资格直接返回忙碌结果，避免重复请求继续进入运行链路。
4. 流式 `reference` 事件到达后先进入前端 `references` 状态。
5. 渲染 `[R1]` 链接时优先按真实用户消息 ID 精确匹配；若上一条用户消息仍是 `optimistic-user-*` 或 `optimistic-edit-user-*`，则仅从当前会话最新 `runId` 的引用批次兜底生成链接。

## 关键文件

- `frontend/user/src/views/chat/useChatWorkspace.ts`：聊天提交同步防重、流式状态维护和 SSE 引用接收。
- `frontend/user/src/views/ChatView.tsx`：助手消息引用编号转链接，处理乐观用户消息和真实引用消息 ID 的过渡期。
- `backend/src/main/java/com/codingx/chat/infrastructure/runtime/ConversationQueueGate.java`：聊天执行会话级并发门控。
- `frontend/user/tests/views/chat/useChatWorkspace.submit.test.ts`：重复提交回归测试。
- `frontend/user/tests/views/ChatView.test.tsx`：引用编号渲染回归测试。
- `backend/src/test/java/com/codingx/chat/infrastructure/runtime/ConversationQueueGateTest.java`：后端同会话重复进入回归测试。

## 边界条件

- 没有输入内容或 token 缺失时不占用提交锁。
- 附件上传失败会恢复输入与附件，并释放提交锁。
- 历史消息回放仍优先使用真实消息 ID 精确匹配，不使用乐观 ID 兜底，避免历史轮次引用误绑。
- 乐观 ID 兜底必须限定在当前助手消息所在会话，并取最新 `runId` 批次。

## 验证方式

- 前端定向测试：`npm run test:run -- tests/views/chat/useChatWorkspace.submit.test.ts tests/views/ChatView.test.tsx`
- 后端定向测试：`mvn test -Dtest=ConversationQueueGateTest`
