# Chat Background Task Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every chat session a durable backend task and show running/completed task state in the session list.

**Architecture:** The backend creates one task id per chat run and uses it for `task.id`, `chat_execution_run.id`, and `chat_execution_run.task_id`. Conversation list responses project the latest task status. The frontend hydrates those fields into workspace snapshots, renders a stable sidebar status slot, and stores per-conversation seen completion timestamps locally.

**Tech Stack:** Spring Boot, MyBatis Plus, Hutool, React, TypeScript, Vitest, Testing Library.

---

## File Structure

- Modify `backend/src/main/java/com/codingx/chat/application/service/ChatStreamExecutionService.java`: create and update durable `Task` records around async chat execution.
- Modify `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatStreamController.java`: create one task id before dispatch and publish the same id in stream meta.
- Modify `backend/src/main/java/com/codingx/chat/interfaces/response/ChatConversationResponse.java`: add task projection fields.
- Modify `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatController.java`: resolve latest task status and add it to conversation responses.
- Modify `backend/src/main/java/com/codingx/task/domain/model/Task.java`: support deterministic task creation with an already-running state if needed by the dispatcher.
- Modify `backend/src/main/java/com/codingx/task/infrastructure/persistence/repository/TaskRepositoryImpl.java`: preserve task timestamps during status restoration and updates.
- Modify `backend/src/test/java/com/codingx/chat/interfaces/controller/ChatControllerListConversationsTest.java`: cover response task fields.
- Modify `backend/src/test/java/com/codingx/chat/application/service/ChatStreamExecutionServiceTest.java`: cover task lifecycle and id consistency.
- Modify `frontend/user/src/views/chat/types.ts`: add task status fields and local unread-completion shape.
- Modify `frontend/user/src/views/chat/chatApi.ts`: parse task fields from conversation list.
- Modify `frontend/user/src/views/chat/localConversationStorage.ts`: persist seen task completion timestamps.
- Modify `frontend/user/src/views/chat/useChatWorkspace.ts`: merge task state, mark unread completions, and clear on open.
- Modify `frontend/user/src/components/Sidebar.tsx`: render running icon and completion dot in the existing right-side slot.
- Modify `frontend/user/tests/views/chat/useChatWorkspace.test.ts` or related hook tests: cover task state hydration and open-to-clear.
- Modify `frontend/user/tests/components/Sidebar.test.tsx`: cover visible indicators.

### Task 1: Backend Task Lifecycle

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/application/service/ChatStreamExecutionService.java`
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatStreamController.java`
- Modify: `backend/src/main/java/com/codingx/task/domain/model/Task.java`
- Test: `backend/src/test/java/com/codingx/chat/application/service/ChatStreamExecutionServiceTest.java`

- [ ] **Step 1: Write the failing lifecycle test**

Add a test that dispatches a chat command with an explicit task id, verifies `taskRepository.save` receives `RUNNING`, then verifies completion saves `SUCCEEDED` and the run uses the same id.

- [ ] **Step 2: Run backend targeted test to verify RED**

Run: `cd backend && mvn -Dtest=ChatStreamExecutionServiceTest test`
Expected: FAIL because dispatcher does not accept an explicit task id and does not persist `Task`.

- [ ] **Step 3: Implement minimal lifecycle support**

Add a dispatcher overload or parameter carrying `taskId`; create `Task.create(... RuntimeType.CLOUD/LOCAL ...)`, call `start()`, save it, and update it in success/failure/rejection/cancellation paths.

- [ ] **Step 4: Run targeted backend test to verify GREEN**

Run: `cd backend && mvn -Dtest=ChatStreamExecutionServiceTest test`
Expected: PASS.

### Task 2: Backend Conversation Task Projection

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/response/ChatConversationResponse.java`
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatController.java`
- Test: `backend/src/test/java/com/codingx/chat/interfaces/controller/ChatControllerListConversationsTest.java`

- [ ] **Step 1: Write the failing response contract test**

Add assertions for `activeTaskId`, `activeTaskStatus`, `lastTaskId`, `lastTaskStatus`, and `lastTaskFinishedAt` on `/api/chat/conversations`.

- [ ] **Step 2: Run controller test to verify RED**

Run: `cd backend && mvn -Dtest=ChatControllerListConversationsTest test`
Expected: FAIL because response fields do not exist.

- [ ] **Step 3: Implement task projection**

Extend the response record and map latest run/task status. Treat running statuses as active and terminal statuses as last-only.

- [ ] **Step 4: Run controller test to verify GREEN**

Run: `cd backend && mvn -Dtest=ChatControllerListConversationsTest test`
Expected: PASS.

### Task 3: Frontend Conversation State Persistence

**Files:**
- Modify: `frontend/user/src/views/chat/types.ts`
- Modify: `frontend/user/src/views/chat/chatApi.ts`
- Modify: `frontend/user/src/views/chat/localConversationStorage.ts`
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`
- Test: `frontend/user/tests/views/chat/useChatWorkspace.test.ts`

- [ ] **Step 1: Write failing hook/storage tests**

Cover hydration from `lastTaskFinishedAt`, setting unread when an inactive conversation completes, and clearing when selected.

- [ ] **Step 2: Run targeted frontend test to verify RED**

Run: `cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.test.ts`
Expected: FAIL because task fields and seen timestamps are not implemented.

- [ ] **Step 3: Implement state merge and persistence**

Parse task fields, store `seenTaskFinishedAtByConversationId`, compute `hasUnreadTaskCompletion`, update on stream finish and list refresh, and clear on `selectConversation`.

- [ ] **Step 4: Run targeted frontend test to verify GREEN**

Run: `cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.test.ts`
Expected: PASS.

### Task 4: Sidebar Indicators

**Files:**
- Modify: `frontend/user/src/components/Sidebar.tsx`
- Test: `frontend/user/tests/components/Sidebar.test.tsx`

- [ ] **Step 1: Write failing sidebar tests**

Assert a running conversation shows an accessible running indicator instead of relative time, and a completed unread conversation shows a completion dot that coexists with time.

- [ ] **Step 2: Run sidebar test to verify RED**

Run: `cd frontend/user && npm run test:run -- tests/components/Sidebar.test.tsx`
Expected: FAIL because indicators are not rendered.

- [ ] **Step 3: Implement compact status slot**

Use `LoaderCircle` or an existing lucide spinner for running state and a themed dot for unread completion. Keep menu hover behavior in the same slot.

- [ ] **Step 4: Run sidebar test to verify GREEN**

Run: `cd frontend/user && npm run test:run -- tests/components/Sidebar.test.tsx`
Expected: PASS.

### Task 5: Full Verification and Commit

**Files:**
- All modified backend/frontend files.

- [ ] **Step 1: Run backend compile**

Run: `cd backend && mvn compile`
Expected: exit code 0.

- [ ] **Step 2: Run backend tests**

Run: `cd backend && mvn test`
Expected: exit code 0.

- [ ] **Step 3: Run frontend build**

Run: `cd frontend/user && npm run build`
Expected: exit code 0.

- [ ] **Step 4: Run frontend tests**

Run: `cd frontend/user && npm run test:run`
Expected: exit code 0.

- [ ] **Step 5: Commit**

Stage only files touched for this feature and commit with:

```bash
git commit -m "feat(chat): 支持会话后台任务执行状态"
```

## Self-Review

- Spec coverage: backend durability, task projection, sidebar running state, unread completion dot, and open-to-clear behavior are all mapped to tasks.
- Placeholder scan: no placeholder rows or unspecified implementation steps remain.
- Type consistency: response fields use `activeTaskId`, `activeTaskStatus`, `lastTaskId`, `lastTaskStatus`, and `lastTaskFinishedAt` consistently across backend and frontend tasks.
