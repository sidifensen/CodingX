# Chat Tool Argument Progress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make long `write` tool arguments visible while the model is still streaming them, so the chat UI shows a running file edit instead of staying blank until the final tool call completes.

**Architecture:** The OpenAI-style stream parser will emit a lightweight tool-call progress callback whenever `tool_calls[].function.arguments` grows. Routing and first-token buffering will preserve that event, and `ChatApplicationService` will convert it into the existing `tool-call progress` SSE payload. The frontend already maps `tool-call progress` into running process cards and pending file diffs, so only a focused regression test is needed there.

**Tech Stack:** Java 21, Spring Boot, JUnit/Mockito, React, Vitest.

---

### Task 1: Backend Tool Argument Progress Contract

**Files:**
- Create: `backend/src/main/java/com/codingx/common/support/ai/AiToolCallDelta.java`
- Modify: `backend/src/main/java/com/codingx/common/support/ai/AiStreamHandler.java`
- Modify: `backend/src/main/java/com/codingx/chat/domain/port/AiChatClient.java`
- Modify: `backend/src/main/java/com/codingx/common/support/ai/OpenAiStyleStreamParser.java`
- Modify: `backend/src/main/java/com/codingx/common/support/ai/FirstTokenBufferingHandler.java`
- Modify: `backend/src/main/java/com/codingx/common/support/ai/AiModelDispatchService.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/ai/RoutingAiChatClient.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/ai/OpenAiCompatibleChatClient.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/ai/DeepSeekOkHttpChatClient.java`
- Test: `backend/src/test/java/com/codingx/support/ai/OpenAiStyleStreamParserTest.java`
- Test: `backend/src/test/java/com/codingx/support/ai/FirstTokenBufferingHandlerTest.java`

- [ ] **Step 1: Write failing parser and buffering tests**

Add tests proving `tool_calls` argument fragments emit progress events before final `onToolCall`, and progress counts as a valid first token without leaking before commit.

- [ ] **Step 2: Run RED**

Run: `cd backend && mvn -Dtest=OpenAiStyleStreamParserTest#parseDispatchesToolCallArgumentProgressBeforeFinalToolCall,FirstTokenBufferingHandlerTest#treatsToolCallDeltaAsFirstContentEventAndReplaysAfterCommit test`

Expected: compilation fails because `AiToolCallDelta` / `onToolCallDelta` do not exist yet.

- [ ] **Step 3: Implement minimal callback plumbing**

Create `AiToolCallDelta`, add default callback methods, emit progress from parser after each argument fragment, and pass it through provider clients, router, first-token buffering, and thinking guard.

- [ ] **Step 4: Run GREEN**

Run the same backend tests and confirm they pass.

### Task 2: Chat SSE Progress For Pending File Writes

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`
- Test: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationToolCallFlowTest.java`

- [ ] **Step 1: Write failing application-service test**

Add a test where the model streams partial `write` arguments, then completes the tool call. Assert a `tool-call progress` SSE payload appears before final completion and includes best-effort `path/content` params for the frontend pending diff.

- [ ] **Step 2: Run RED**

Run: `cd backend && mvn -Dtest=ChatApplicationToolCallFlowTest#sendMessagePublishesWriteToolArgumentProgressBeforeToolCompletes test`

Expected: compilation fails or verification fails because the application handler ignores tool argument progress.

- [ ] **Step 3: Implement progress publication**

In `buildStreamHandler`, handle `onToolCallDelta` by publishing throttled `tool-call progress` events. For partial `write` JSON, extract `path` and partial `content` into a params object so `buildPendingFileDiffsFromToolParams` can render a growing diff.

- [ ] **Step 4: Run GREEN**

Run the application-service test and confirm it passes.

### Task 3: Frontend Progress Regression

**Files:**
- Test: `frontend/user/tests/views/chat/useChatWorkspace.test.ts`

- [ ] **Step 1: Write frontend regression**

Add a test that receives two `tool-call progress` events for the same `write` call and confirms the pending file diff updates from the first content chunk to the second content chunk.

- [ ] **Step 2: Run frontend test**

Run: `cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.test.ts -t "updates pending file diffs from local write progress events"`

Expected: pass if existing frontend merge logic is sufficient; otherwise make the smallest frontend fix.

### Task 4: Documentation And Verification

**Files:**
- Modify: `docs/features/chat/local-tool-runtime.md`

- [ ] **Step 1: Document stream progress contract**

Update the local tool runtime document to state that long streamed tool arguments publish `tool-call progress` events before tool execution.

- [ ] **Step 2: Verify affected backend and frontend**

Run focused backend tests, `mvn compile`, and the targeted frontend test. If time permits, run the broader affected test groups.

- [ ] **Step 3: Commit only task-owned files**

Stage only the parser/router/application/frontend-test/docs files touched by this plan and commit with `fix(chat): 实时展示工具写入进度`.
