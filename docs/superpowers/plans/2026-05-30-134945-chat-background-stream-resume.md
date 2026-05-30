# Chat Background Stream Resume Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure chat tasks keep running after navigation and running conversations can resume live SSE output.

**Architecture:** Keep database task state as the authority, add bounded in-memory SSE buffering for active runs, and make the frontend subscribe to the conversation stream when opening a running conversation. Navigation away from a stream only detaches the local SSE consumer; explicit cancel remains the only path that stops backend work.

**Tech Stack:** Spring Boot MVC `SseEmitter`, Java unit tests with JUnit/Mockito, React hook tests with Vitest and Testing Library.

---

### Task 1: Backend SSE Runtime Buffer

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/stream/ChatSseRegistry.java`
- Modify: `backend/src/test/java/com/codingx/chat/infrastructure/stream/ChatSseRegistryTest.java`

- [x] **Step 1: Write failing tests**

Add tests proving a publish before registration is replayed to the next emitter and `complete` clears the replay buffer.

- [x] **Step 2: Run red test**

Run: `cd backend && mvn -Dtest=ChatSseRegistryTest test`

Expected: fails because `ChatSseRegistry` currently drops events when no emitter exists.

- [x] **Step 3: Implement bounded runtime buffer**

Store the latest active-run events per conversation before sending. On `register`, send buffered events to the new emitter. On `complete`, remove emitters and the buffer.

- [x] **Step 4: Run green test**

Run: `cd backend && mvn -Dtest=ChatSseRegistryTest test`

Expected: tests pass.

### Task 2: Frontend Running Conversation Resume

**Files:**
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`
- Modify: `frontend/user/tests/views/chat/useChatWorkspace.submit.test.ts`

- [x] **Step 1: Write failing tests**

Add Hook tests for opening a running conversation and for `startNewConversation()` not calling cancel. Keep explicit cancel coverage intact.

- [ ] **Step 2: Run red test**

Run: `cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.submit.test.ts`

Expected: running conversation does not subscribe to `/stream`, and new-conversation flow calls cancel.

- [x] **Step 3: Implement resume subscription**

Add a helper that creates a stream session for an already-running conversation, appends or reuses a streaming assistant placeholder, fetches `/api/chat/conversations/{id}/stream`, and consumes events with existing SSE handlers.

- [x] **Step 4: Change navigation detach semantics**

Remove backend cancel from `startNewConversation`; leave `cancelCurrentStream` as the explicit cancellation path.

- [x] **Step 5: Run green test**

Run: `cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.submit.test.ts`

Expected: targeted tests pass.

### Task 3: Documentation and Verification

**Files:**
- Modify: `docs/features/index.md`
- Create: `docs/features/chat/background-stream-resume.md`

- [x] **Step 1: Document current behavior**

Add a concise feature doc covering the task tables, stream buffer, frontend resume subscription, and verification path.

- [x] **Step 2: Verify backend**

Run: `cd backend && mvn compile && mvn test`

Expected: build and tests pass.

- [x] **Step 3: Verify frontend**

Run: `cd frontend/user && npm run build && npm run test:run`

Expected: build and tests pass.

- [x] **Step 4: Browser verification**

Use `/web-access` CDP browser validation against `http://localhost:5002`; capture evidence in `logs/`.
