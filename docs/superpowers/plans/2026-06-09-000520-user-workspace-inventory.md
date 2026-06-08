# User Workspace Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show all current-user workspaces in the user chat sidebar, including workspaces with no conversations.

**Architecture:** Add a user-side read-only workspace inventory API under `/api/chat/workspaces`, then merge the returned inventory with existing local workspace snapshots in `useChatWorkspace`. Local snapshots remain authoritative for conversations and UI state; server inventory fills missing empty groups.

**Tech Stack:** Spring Boot 3, MyBatis-Plus, Hutool, React 19, TypeScript, Vitest.

---

### Task 1: Backend User Workspace Inventory

**Files:**
- Modify: `backend/src/main/java/com/codingx/workspace/infrastructure/repository/WorkspaceRepositoryImpl.java`
- Create: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatWorkspaceInventoryService.java`
- Create: `backend/src/main/java/com/codingx/chat/interfaces/response/ChatWorkspaceResponse.java`
- Create or Modify: `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatWorkspaceInventoryController.java`
- Modify: `backend/src/test/java/com/codingx/workspace/infrastructure/repository/WorkspaceRepositoryImplTest.java`
- Create: `backend/src/test/java/com/codingx/chat/interfaces/controller/ChatWorkspaceInventoryControllerTest.java`

- [ ] **Step 1: Write failing repository and controller tests**

Add repository coverage for current-user filtering and controller coverage for `/api/chat/workspaces`.

- [ ] **Step 2: Run backend target tests and verify red**

Run: `cd backend && mvn -Dtest=WorkspaceRepositoryImplTest,ChatWorkspaceInventoryControllerTest test`

Expected: compile or test failure because inventory API/service methods do not exist.

- [ ] **Step 3: Implement repository, service, response, and controller**

Repository should query `created_by`, `deleted = 0`, order by `runtime_target`, `updated_at desc`, `id desc`; service should read `StpUtil.getLoginIdAsLong()` and map to response records.

- [ ] **Step 4: Run backend target tests and verify green**

Run: `cd backend && mvn -Dtest=WorkspaceRepositoryImplTest,ChatWorkspaceInventoryControllerTest test`

Expected: exit code 0.

### Task 2: Frontend Inventory Merge

**Files:**
- Modify: `frontend/user/src/views/chat/types.ts`
- Modify: `frontend/user/src/views/chat/chatApi.ts`
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`
- Modify: `frontend/user/tests/views/chat/useChatWorkspace.workspaceSwitch.test.ts`

- [ ] **Step 1: Write failing hook test**

Add a test that mocks `/api/chat/workspaces` returning `D:/code/CodingX` with no local snapshot and asserts `workspaceGroups` contains an empty `CodingX` group.

- [ ] **Step 2: Run frontend target test and verify red**

Run: `cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.workspaceSwitch.test.ts`

Expected: failure because `ChatApi.listWorkspaces` is not called or empty service workspaces are not merged.

- [ ] **Step 3: Implement API and merge logic**

Add `WorkspaceInventoryItem` type, `ChatApi.listWorkspaces`, state for service inventory, merge helper, and refresh dependency so local snapshots keep priority while empty server workspaces appear.

- [ ] **Step 4: Run frontend target test and verify green**

Run: `cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.workspaceSwitch.test.ts`

Expected: exit code 0.

### Task 3: Documentation And Verification

**Files:**
- Modify: `docs/features/index.md`
- Create: `docs/features/chat/user-workspace-inventory.md`

- [ ] **Step 1: Update feature docs**

Document purpose, entrypoint, core flow, files, and verification.

- [ ] **Step 2: Run changed-scope verification**

Run:

```bash
cd backend && mvn compile
cd backend && mvn test
cd frontend/user && npm run build
cd frontend/user && npm run test:run
```

Expected: commands exit 0 unless unrelated pre-existing dirty work causes failures, in which case record the exact failure and scope.

- [ ] **Step 3: Stage and commit only this task's files**

Commit message: `feat(chat): 展示用户全部工作空间`
