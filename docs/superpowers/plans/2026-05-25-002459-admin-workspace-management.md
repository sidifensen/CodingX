# 管理端工作空间管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为管理端新增只读工作空间管理页，支持分页、搜索、运行目标筛选和会话数量展示。

**Architecture:** 后端在 `com.codingx.admin` 新增工作空间查询控制器与服务，复用 `WorkspaceRepository` 读取 `workspace` 表并统计会话数；前端在 `AdminChatApi` 增加列表方法，并新增 `/workspaces` 页面复用统一管理端表格和分页组件。

**Tech Stack:** Spring Boot, MyBatis-Plus, JUnit 5, Mockito, React, TypeScript, Vite, Vitest, Testing Library, Tailwind-style design tokens.

---

### Task 1: 后端工作空间查询契约

**Files:**
- Modify: `backend/src/main/java/com/codingx/workspace/domain/repository/WorkspaceRepository.java`
- Modify: `backend/src/main/java/com/codingx/workspace/infrastructure/repository/WorkspaceRepositoryImpl.java`
- Create: `backend/src/main/java/com/codingx/workspace/domain/model/AdminWorkspaceQuery.java`
- Create: `backend/src/main/java/com/codingx/workspace/domain/model/AdminWorkspaceRecord.java`
- Create: `backend/src/main/java/com/codingx/workspace/domain/model/AdminWorkspacePage.java`
- Test: `backend/src/test/java/com/codingx/workspace/infrastructure/repository/WorkspaceRepositoryImplTest.java`

- [ ] **Step 1: Write failing repository test**

Add a test asserting that `pageForAdmin` returns only active records, labels no data itself, and includes conversation counts from active conversations.

Run: `cd backend && mvn -Dtest=WorkspaceRepositoryImplTest#pageForAdminReturnsWorkspaceRecordsWithConversationCounts test`

Expected: FAIL because `AdminWorkspaceQuery` and `pageForAdmin` do not exist.

- [ ] **Step 2: Implement minimal repository query**

Add query/record/page model classes with comments. Extend `WorkspaceRepository` with `pageForAdmin(AdminWorkspaceQuery query)`. Implement filtering by `deleted=0`, optional `runtimeTarget`, optional keyword, sorting by `updatedAt desc, id desc`, in-memory pagination consistent with existing admin conversation service, and count active conversations per workspace.

- [ ] **Step 3: Verify repository test**

Run: `cd backend && mvn -Dtest=WorkspaceRepositoryImplTest#pageForAdminReturnsWorkspaceRecordsWithConversationCounts test`

Expected: PASS.

### Task 2: 后端管理端 API

**Files:**
- Create: `backend/src/main/java/com/codingx/admin/application/service/AdminWorkspaceService.java`
- Create: `backend/src/main/java/com/codingx/admin/interfaces/controller/AdminWorkspaceController.java`
- Create: `backend/src/main/java/com/codingx/workspace/interfaces/response/AdminWorkspaceListItemResponse.java`
- Test: `backend/src/test/java/com/codingx/admin/application/service/AdminWorkspaceServiceTest.java`
- Test: `backend/src/test/java/com/codingx/admin/interfaces/controller/AdminWorkspaceControllerTest.java`

- [ ] **Step 1: Write failing service and controller tests**

Service test asserts cloud/local labels and pagination metadata. Controller test asserts `GET /api/admin/workspaces` returns `ApiResponse` and passes `runtimeTarget`.

Run: `cd backend && mvn -Dtest=AdminWorkspaceServiceTest,AdminWorkspaceControllerTest test`

Expected: FAIL because service/controller/response classes do not exist.

- [ ] **Step 2: Implement service/controller/response**

Create a service that normalizes `current` and `size`, delegates to `WorkspaceRepository.pageForAdmin`, maps `cloud` to `云端`, `local` to `本地`, and returns `PageResult<AdminWorkspaceListItemResponse>`. Create controller under `/api/admin/workspaces`.

- [ ] **Step 3: Verify API tests**

Run: `cd backend && mvn -Dtest=AdminWorkspaceServiceTest,AdminWorkspaceControllerTest test`

Expected: PASS.

### Task 3: 前端 API 与路由

**Files:**
- Modify: `frontend/admin/src/api/adminChatApi.ts`
- Modify: `frontend/admin/src/App.tsx`
- Modify: `frontend/admin/src/components/Layout.tsx`
- Test: `frontend/admin/tests/api/adminChatApi.test.ts`
- Test: `frontend/admin/tests/components/Layout.test.tsx`

- [ ] **Step 1: Write failing frontend API/routing tests**

Add API test for `listWorkspaces` query parameters. Add layout/app expectation for “工作空间” navigation.

Run: `cd frontend/admin && npm run test:run -- adminChatApi.test.ts Layout.test.tsx`

Expected: FAIL because `listWorkspaces` and nav item do not exist.

- [ ] **Step 2: Implement API types, method and route shell**

Add `AdminWorkspace` and `AdminWorkspaceQuery` interfaces. Implement `AdminChatApi.listWorkspaces`. Add nav item and route import for `WorkspacePage`.

- [ ] **Step 3: Verify API/routing tests**

Run: `cd frontend/admin && npm run test:run -- adminChatApi.test.ts Layout.test.tsx`

Expected: PASS after the page component exists in Task 4.

### Task 4: 前端工作空间管理页面

**Files:**
- Create: `frontend/admin/src/pages/WorkspacePage.tsx`
- Create: `frontend/admin/tests/pages/WorkspacePage.test.tsx`

- [ ] **Step 1: Write failing page tests**

Test renders list row, filters by runtime target, shows skeleton rows while loading, shows empty state, and shows error message.

Run: `cd frontend/admin && npm run test:run -- WorkspacePage.test.tsx`

Expected: FAIL because `WorkspacePage` does not exist.

- [ ] **Step 2: Implement page**

Create `WorkspacePage` using `DataTableCard`, stats cards, keyword input, runtime target select, refresh button, skeleton rows, empty state, error banner, and theme-token classes only. Do not use native browser dialogs.

- [ ] **Step 3: Verify page tests**

Run: `cd frontend/admin && npm run test:run -- WorkspacePage.test.tsx`

Expected: PASS.

### Task 5: Full Verification And Browser Check

**Files:**
- No code changes unless verification exposes defects.

- [ ] **Step 1: Run backend verification**

Run: `cd backend && mvn compile && mvn test`

Expected: PASS.

- [ ] **Step 2: Run frontend verification**

Run: `cd frontend/admin && npm run build && npm run test:run`

Expected: PASS.

- [ ] **Step 3: Start backend and frontend for visual check**

Check ports 5001 and 5002, stop conflicting processes if needed, then start backend and frontend in background.

Run: `cd backend && mvn spring-boot:run`

Run: `cd frontend/admin && npm run dev -- --host 127.0.0.1 --port 5002`

Expected: backend reachable at `http://localhost:5001`, frontend reachable at `http://localhost:5002`.

- [ ] **Step 4: Browser inspect `/workspaces`**

Use Chrome DevTools MCP to log in with dev defaults if needed and navigate to `/workspaces`.

Expected: page title “工作空间管理” is visible, table renders without console/runtime errors, and dark/light theme remains readable.

- [ ] **Step 5: Review and commit**

Review changed files, stage only this task's files, and commit with Chinese message:

`feat(admin): 新增工作空间管理页面`
