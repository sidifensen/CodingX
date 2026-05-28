# Chat Stream Submit Citation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复聊天流式提交重复进入和流式引用链接延迟可点击的问题。

**Architecture:** 前端在提交函数入口使用同步 ref 锁覆盖 React 状态更新前的竞态窗口，引用链接在乐观消息 ID 场景下使用当前会话最新引用批次兜底。后端队列门控拒绝同一会话运行中再次进入，防止绕过前端保护。

**Tech Stack:** React、TypeScript、Vitest、Spring Boot、JUnit 5。

---

### Task 1: 前端回归测试

**Files:**
- Modify: `frontend/user/tests/views/chat/useChatWorkspace.submit.test.ts`
- Modify: `frontend/user/tests/views/ChatView.test.tsx`

- [ ] **Step 1: Write failing tests**

新增一个 `useChatWorkspace.submit.test.ts` 用例：第一次提交保持 `/api/chat/stream` pending，第二次立即调用 `submitMessage()`，断言 stream 请求次数仍为 1。新增一个 `ChatView.test.tsx` 用例：上一条用户消息为 `optimistic-user-*`，引用 `messageId` 为真实 ID，断言 `[R1]` 渲染为链接。

- [ ] **Step 2: Run tests to verify RED**

Run: `cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.submit.test.ts tests/views/ChatView.test.tsx -t "重复提交|乐观用户消息"`

Expected: 两个新增用例至少一个失败，失败原因分别对应重复 stream 请求或 `[R1]` 不是链接。

### Task 2: 前端实现

**Files:**
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`
- Modify: `frontend/user/src/views/ChatView.tsx`

- [ ] **Step 1: Implement submit lock**

在 `useChatWorkspace` 中新增 `submitMessageInFlightRef`，`submitMessage` 进入 token 校验后立即检查并设置该锁，外层 `finally` 释放。

- [ ] **Step 2: Implement optimistic citation fallback**

在 `ChatView` 中保留真实 ID 精确匹配；当 `referenceMessageId` 是乐观用户 ID 且精确匹配为空时，筛选同会话最新 `runId` 的引用批次构建链接。

- [ ] **Step 3: Run GREEN tests**

Run: `cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.submit.test.ts tests/views/ChatView.test.tsx -t "重复提交|乐观用户消息|引用编号"`

Expected: 新增用例和既有引用用例通过。

### Task 3: 后端回归测试和实现

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/infrastructure/runtime/ConversationQueueGateTest.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/runtime/ConversationQueueGate.java`

- [ ] **Step 1: Write failing backend test**

新增 JUnit 用例：同一 `conversationId` 未释放时第二次 `tryAcquire` 返回 rejected。

- [ ] **Step 2: Run test to verify RED**

Run: `cd backend && mvn test -Dtest=ConversationQueueGateTest#inMemoryAcquireRejectsSameConversationWhenAlreadyActive`

Expected: 当前实现返回 allowed，测试失败。

- [ ] **Step 3: Implement gate rejection**

修改进程内和 Redis 门控：若同一 `conversationId` 已活动，返回 `QueueAcquireResult.rejected(ErrorMessageCatalog.CHAT_QUEUE_BUSY)`，不要再次授权。

- [ ] **Step 4: Run backend tests**

Run: `cd backend && mvn test -Dtest=ConversationQueueGateTest`

Expected: `ConversationQueueGateTest` 全部通过。

### Task 4: 文档和验证

**Files:**
- Modify or Create: `docs/features/chat/stream-submit-citation.md`
- Modify: `docs/features/index.md`

- [ ] **Step 1: Update feature docs**

记录聊天流式提交防重、引用链接实时绑定、后端会话门控的当前实现。

- [ ] **Step 2: Run scoped verification**

Run: `cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.submit.test.ts tests/views/ChatView.test.tsx`

Run: `cd backend && mvn test -Dtest=ConversationQueueGateTest`

Expected: 两条命令均 exit 0。
