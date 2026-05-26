# Local Chat Local-Only Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make local runtime chats and folder selection stay local by default without creating cloud database workspace, conversation, or message records.

**Architecture:** Keep cloud persistence unchanged. Local runtime uses client local snapshots plus a backend local-only streaming path that runs tools/model against a repository path without creating database workspace or conversation records.

**Tech Stack:** Spring Boot, MyBatis Plus, Vitest, React hook state, browser localStorage.

---

### Task 1: Stop Local Folder Binding From Creating Workspace Rows

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatWorkspaceBindingService.java`
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatWorkspaceBindingController.java`
- Test: `backend/src/test/java/com/codingx/chat/application/service/ChatWorkspaceBindingServiceTest.java`

- [ ] **Step 1: Write failing backend test**

Add a test proving `bindRepositoryPathForCurrentUser` returns a normalized path and does not call `workspaceMapper.insert`.

- [ ] **Step 2: Run focused backend test**

Run: `mvn -Dtest=ChatWorkspaceBindingServiceTest test`
Expected: FAIL because binding currently creates a workspace id.

- [ ] **Step 3: Implement local-only binding**

Remove `findOrCreateWorkspace` from the binding flow. Keep path validation and `repositoryPathByUserId`. Make `WorkspaceBindingResult.workspaceId` nullable.

- [ ] **Step 4: Run focused backend test**

Run: `mvn -Dtest=ChatWorkspaceBindingServiceTest test`
Expected: PASS.

### Task 2: Stop Frontend Local Mode From Using Cloud Workspace Ids

**Files:**
- Modify: `frontend/user/src/views/chat/chatApi.ts`
- Modify: `frontend/user/src/host/types.ts`
- Modify: `frontend/user/src/host/useHostContext.ts`
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`
- Test: `frontend/user/tests/views/chat/useChatWorkspace.test.ts`

- [ ] **Step 1: Write failing frontend tests**

Add tests that local binding with null `workspaceId` keeps `workspaceId` state null and that submit URL has `runtimeTarget=local` with no `workspaceId`.

- [ ] **Step 2: Run focused frontend tests**

Run: `npm run test:run -- tests/views/chat/useChatWorkspace.test.ts`
Expected: FAIL because local mode currently consumes/passes workspace id.

- [ ] **Step 3: Implement frontend local-only URL and state rules**

Allow binding responses with nullable workspace id. In local runtime, do not set `workspaceId` from binding and do not pass it to `buildStreamRequestUrl`. Add `runtimeTarget=local` to local stream URLs.

- [ ] **Step 4: Run focused frontend tests**

Run: `npm run test:run -- tests/views/chat/useChatWorkspace.test.ts`
Expected: PASS.

### Task 3: Add Backend Local-Only Stream Path

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatStreamController.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatStreamExecutionService.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/command/SendChatMessageCommand.java`
- Test: `backend/src/test/java/com/codingx/chat/interfaces/controller/ChatStreamControllerTest.java`
- Test: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationServiceTest.java`

- [ ] **Step 1: Write failing backend stream tests**

Add a test that `runtimeTarget=local` with a local conversation id does not call `createConversation` and dispatches with the supplied local id semantics.

- [ ] **Step 2: Run focused backend tests**

Run: `mvn -Dtest=ChatStreamControllerTest,ChatApplicationServiceTest test`
Expected: FAIL because stream currently always creates a database conversation for new chats.

- [ ] **Step 3: Implement transient local execution**

Add command local-only metadata and route local stream requests through a transient execution branch that publishes SSE events and avoids database conversation/message writes. Reuse repository path binding for tool working directory.

- [ ] **Step 4: Run focused backend tests**

Run: `mvn -Dtest=ChatStreamControllerTest,ChatApplicationServiceTest test`
Expected: PASS.

### Task 4: Full Verification and Commit

**Files:**
- All modified files from Tasks 1-3.

- [ ] **Step 1: Run backend verification**

Run: `mvn compile` and `mvn test` from `backend`.
Expected: BUILD SUCCESS, 0 failures.

- [ ] **Step 2: Run frontend verification**

Run: `npm run build` and `npm run test:run` from `frontend/user`.
Expected: build succeeds and tests pass.

- [ ] **Step 3: Review git diff**

Run: `git status --short` and stage only files touched for local-only persistence.

- [ ] **Step 4: Commit**

Commit message: `feat(chat): 本地模式默认不落云端工作空间`
