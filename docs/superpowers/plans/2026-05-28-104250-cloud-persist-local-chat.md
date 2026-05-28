# Cloud Persist Local Chat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every chat request, including Electron local runtime requests, create or reuse a cloud database workspace and persist conversations, messages, tasks, runs, and traces.

**Architecture:** Treat `runtimeTarget` as workspace classification, not as a persistence switch. Cloud requests without a workspace use the default cloud history workspace; local requests without a selected folder use a default local history workspace; local requests with `repositoryPath` use a local workspace keyed by normalized `working_directory`.

**Tech Stack:** Spring Boot 3, MyBatis Plus, Hutool, React/Vitest, Electron host bridge.

---

### Task 1: Backend Workspace Creation Contract

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/chat/ChatWorkspaceBindingServiceTest.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatWorkspaceBindingService.java`
- Modify: `backend/src/main/java/com/codingx/workspace/infrastructure/repository/WorkspaceRepositoryImpl.java`

- [x] **Step 1: Write failing tests**

Add tests asserting repository binding creates or reuses a `runtime_target='local'` workspace with normalized slash path and returns `workspaceId`.

- [x] **Step 2: Verify RED**

Run `cd backend; mvn -Dtest=ChatWorkspaceBindingServiceTest test`. Expected failure: binding result still has `workspaceId=null` and mapper insert is never called.

- [x] **Step 3: Implement minimal code**

Add repository methods that ensure default local history workspace and local path workspace. Update binding service to validate path, store in memory, create/reuse local workspace, and return its id/name/path.

- [x] **Step 4: Verify GREEN**

Run `cd backend; mvn -Dtest=ChatWorkspaceBindingServiceTest test`. Expected pass.

### Task 2: Backend Stream Persistence Contract

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/interfaces/controller/ChatStreamControllerTest.java`
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatStreamController.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatConversationApplicationService.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatStreamExecutionService.java`

- [x] **Step 1: Write failing tests**

Change the local runtime stream test to require `createConversation(new CreateConversationCommand(null, workspaceId), userId)`, `localOnly=false`, `runtimeTarget='local'` in meta, and repository path still carried in command.

- [x] **Step 2: Verify RED**

Run `cd backend; mvn -Dtest=ChatStreamControllerTest test`. Expected failure: old controller skips conversation creation and emits `localOnly=true`.

- [x] **Step 3: Implement minimal code**

Make `runtimeTarget=local` persist by default. Keep `localOnly` only for future explicit temporary mode if needed, but no current Electron request should enter that path. Ensure missing `workspaceId` in local runtime resolves to default local history workspace.

- [x] **Step 4: Verify GREEN**

Run `cd backend; mvn -Dtest=ChatStreamControllerTest test`. Expected pass.

### Task 3: Frontend Request Contract

**Files:**
- Modify: `frontend/user/tests/views/chat/useChatWorkspace.test.ts`
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`
- Modify: `frontend/user/src/views/chat/chatApi.ts`
- Modify: `frontend/user/src/host/useHostContext.ts`

- [x] **Step 1: Write failing tests**

Update local send tests so a selected local folder must send both `runtimeTarget=local` and `workspaceId=<bound id>`, then reload conversation data from cloud APIs after stream completion.

- [x] **Step 2: Verify RED**

Run `cd frontend/user; npm run test:run -- tests/views/chat/useChatWorkspace.test.ts`. Expected failure: request does not include `workspaceId` and old test expects no cloud history fetch.

- [x] **Step 3: Implement minimal code**

Normalize binding response ids, ensure local folder binding result is stored in host context, and make local stream requests include workspaceId when available. Preserve local snapshot only as UI fallback, not as the primary history source.

- [x] **Step 4: Verify GREEN**

Run `cd frontend/user; npm run test:run -- tests/views/chat/useChatWorkspace.test.ts`. Expected pass for updated tests.

### Task 4: Documentation and Verification

**Files:**
- Modify: `docs/features/index.md`
- Create/modify: `docs/features/chat/cloud-persisted-local-chat.md`

- [x] **Step 1: Document current behavior**

Explain cloud/default local/local-folder workspace creation, conversation persistence, and local snapshot fallback.

- [x] **Step 2: Run verification**

Run backend compile/test and frontend build/test commands required by `AGENTS.md`. If unrelated dirty-session tests fail, isolate and report them without modifying unrelated files.

- [x] **Step 3: Review and commit**

Review only files touched by this task. Commit with Chinese message `feat(chat): 本地会话统一云端持久化`.
