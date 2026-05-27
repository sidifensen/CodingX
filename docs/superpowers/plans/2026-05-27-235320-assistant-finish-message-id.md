# AI 回复完成事件真实消息 ID Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AI 回复生成完成后，前端立即拿到真实助手消息 ID，使底部操作栏按钮不再等待历史回放才可用。

**Architecture:** 后端扩展 `finish` SSE 事件载荷，云端落库链路传入 `assistantMessageId`；前端在 `finish` 处理器中回填当前乐观助手消息的真实 ID。本地临时会话继续使用旧三参完成事件，不伪造云端消息 ID。

**Tech Stack:** Spring Boot、SSE、React、Vitest、JUnit/Mockito。

---

### Task 1: 后端完成事件载荷

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/domain/port/ChatStreamPublisher.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/stream/SseChatStreamPublisher.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/stream/NoopChatStreamPublisher.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`
- Test: `backend/src/test/java/com/codingx/chat/infrastructure/stream/SseChatStreamPublisherTest.java`

- [ ] **Step 1: Write failing backend test**

Add a test that calls `publishAssistantCompleted(1L, 102L, "回答", "标题")` and verifies the `finish` payload contains `assistantMessageId=102L`.

- [ ] **Step 2: Run backend test to verify RED**

Run: `cd backend && mvn -Dtest=SseChatStreamPublisherTest test`
Expected: compile or assertion failure because the four-argument contract does not exist yet.

- [ ] **Step 3: Implement backend contract**

Add four-argument completion publishing, keep the three-argument default path for no-message-ID cases, and update cloud chat completion call sites to pass `assistantMessage.getId()`.

- [ ] **Step 4: Run backend test to verify GREEN**

Run: `cd backend && mvn -Dtest=SseChatStreamPublisherTest test`
Expected: test passes.

### Task 2: 前端 finish 回填

**Files:**
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`
- Test: `frontend/user/tests/views/chat/useChatWorkspace.test.ts`

- [ ] **Step 1: Write failing frontend test**

Add a hook test that streams `finish` with `assistantMessageId:"102"` and asserts the current assistant message ID is immediately `102`.

- [ ] **Step 2: Run frontend test to verify RED**

Run: `cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.test.ts --testNamePattern "finish.*assistantMessageId"`
Expected: fails because the message remains `optimistic-assistant-*`.

- [ ] **Step 3: Implement frontend回填**

In `finish` handling, normalize `assistantMessageId`; if present, update the matching optimistic assistant message `id` and `conversationId` while preserving content/process/search state.

- [ ] **Step 4: Run frontend test to verify GREEN**

Run: `cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.test.ts --testNamePattern "finish.*assistantMessageId"`
Expected: test passes.

### Task 3: Documentation and Final Verification

**Files:**
- Modify: `docs/features/index.md`
- Create or modify: `docs/features/chat/assistant-finish-message-id.md`

- [ ] **Step 1: Document the feature**

Record the finish event contract and frontend回填 behavior.

- [ ] **Step 2: Run scoped verification**

Run backend and frontend targeted tests listed above.

- [ ] **Step 3: Review and commit**

Review diff for unrelated changes, stage only this task's files, and commit with message `fix(chat): 回填AI回复完成事件消息标识`。
