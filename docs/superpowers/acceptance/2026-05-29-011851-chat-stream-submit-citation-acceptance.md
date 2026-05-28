# Acceptance Criteria: Chat Stream Submit Citation

**Spec:** `docs/superpowers/specs/2026-05-29-011851-chat-stream-submit-citation-design.md`
**Date:** 2026-05-29
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 快速连续调用聊天提交动作时只允许一个流式请求进入网络层。 | Logic | 输入框存在非空内容，第一次 `/api/chat/stream` 请求保持 pending。 | 第二次 `submitMessage()` 返回后，`fetch` 中匹配 `/api/chat/stream` 的调用次数仍为 1。 |
| AC-002 | 流式阶段上一条用户消息是乐观 ID 时，引用事件携带真实消息 ID 也能让 `[R1]` 立即变成链接。 | Logic | `messages` 中上一条用户消息 ID 为 `optimistic-user-*`，`references` 中存在同会话最新运行批次的 URL。 | `ChatView` 渲染后 `[R1]` 节点为链接，`href` 等于 reference 的 URL。 |
| AC-003 | 历史回放场景仍优先按真实用户消息 ID 精确匹配引用。 | Logic | `messages` 中上一条用户消息 ID 为真实 ID，`references` 中存在多个运行批次。 | 只有 `messageId` 等于上一条用户消息 ID 的引用参与 `[R1]` 链接构建。 |
| AC-004 | 后端同一会话运行中再次获取队列资格会被拒绝。 | Logic | `ConversationQueueGate` 已对 conversationId `1001` 获取执行资格且未释放。 | 第二次 `tryAcquire(1001)` 返回 `allowed=false`，拒绝原因是 `CHAT_QUEUE_BUSY` 对应中文文案。 |
| AC-005 | 修复不改变聊天输入区主题、布局和原有引用历史能力。 | Logic | 运行现有 ChatView 和 useChatWorkspace 相关测试。 | 相关测试通过，不出现主题或布局断言回退。 |
