# 管理端工作空间会话详情 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让管理端工作空间列表可点击进入详情页，并分页查看该工作空间下的会话。

**Architecture:** 后端在工作空间管理接口下新增会话子资源，服务层先校验空间存在再按 `workspace_id` 查询会话；前端新增工作空间详情页，并复用现有管理端会话列表响应、表格和详情跳转。

**Tech Stack:** Spring Boot, MyBatis-Plus, JUnit 5, Mockito, React, TypeScript, React Router, Vitest, Testing Library.

---

### Task 1: 后端工作空间会话接口

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/domain/repository/conversation/ChatConversationRepository.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/repository/conversation/ChatConversationRepositoryImpl.java`
- Modify: `backend/src/main/java/com/codingx/admin/application/service/AdminWorkspaceService.java`
- Modify: `backend/src/main/java/com/codingx/admin/interfaces/controller/AdminWorkspaceController.java`
- Test: `backend/src/test/java/com/codingx/chat/application/service/AdminWorkspaceServiceTest.java`
- Test: `backend/src/test/java/com/codingx/chat/interfaces/controller/AdminWorkspaceControllerTest.java`
- Test: `backend/src/test/java/com/codingx/chat/infrastructure/persistence/repository/conversation/ChatConversationRepositoryImplTest.java`

- [ ] **Step 1: Write failing backend tests**

Add service, controller, and repository tests for `pageWorkspaceConversations` and `GET /api/admin/workspaces/{workspaceId}/conversations`.

Run: `cd backend && mvn -Dtest=AdminWorkspaceServiceTest,AdminWorkspaceControllerTest,ChatConversationRepositoryImplTest test`

Expected: FAIL because `findAllByWorkspaceId` and `pageWorkspaceConversations` do not exist.

- [ ] **Step 2: Implement minimal backend code**

Add repository method `findAllByWorkspaceId(Long workspaceId, String keyword)`, implement workspace/deleted/keyword filters, add service pagination and status label mapping, and expose controller endpoint.

- [ ] **Step 3: Verify backend tests**

Run: `cd backend && mvn -Dtest=AdminWorkspaceServiceTest,AdminWorkspaceControllerTest,ChatConversationRepositoryImplTest test`

Expected: PASS.

### Task 2: 前端工作空间详情入口与页面

**Files:**
- Modify: `frontend/admin/src/api/adminChatApi.ts`
- Modify: `frontend/admin/src/App.tsx`
- Modify: `frontend/admin/src/pages/WorkspacePage.tsx`
- Create: `frontend/admin/src/pages/WorkspaceDetailPage.tsx`
- Test: `frontend/admin/tests/api/adminChatApi.test.ts`
- Test: `frontend/admin/tests/pages/WorkspacePage.test.tsx`
- Create: `frontend/admin/tests/pages/WorkspaceDetailPage.test.tsx`

- [ ] **Step 1: Write failing frontend tests**

Add API test for `listWorkspaceConversations`, list-page link test, and detail-page render/search/loading/empty/error tests.

Run: `cd frontend/admin && npm run test:run -- adminChatApi.test.ts WorkspacePage.test.tsx WorkspaceDetailPage.test.tsx`

Expected: FAIL because API method, route, link, and detail page do not exist.

- [ ] **Step 2: Implement frontend code**

Add API method and route `/workspaces/:workspaceId`; convert workspace name to a link; implement detail page with search, refresh, table, pagination, skeleton, empty state, error banner, and `/tasks/:id` detail links.

- [ ] **Step 3: Verify frontend tests**

Run: `cd frontend/admin && npm run test:run -- adminChatApi.test.ts WorkspacePage.test.tsx WorkspaceDetailPage.test.tsx`

Expected: PASS.

### Task 3: Full Verification, Browser Check, Commit

**Files:**
- No new files unless verification exposes defects.

- [ ] **Step 1: Run backend verification**

Run: `cd backend && mvn compile`

Run targeted tests from Task 1. If full `mvn test` is blocked by unrelated dirty work, record the exact blocker.

- [ ] **Step 2: Run frontend verification**

Run: `cd frontend/admin && npm run build`

Run targeted tests from Task 2.

- [ ] **Step 3: Start services and browser verify**

Start backend on `http://localhost:5001` and admin frontend on `http://localhost:5003`, then open `/workspaces`, click a workspace, and confirm `/workspaces/:workspaceId` renders会话列表 without console/runtime errors.

- [ ] **Step 4: Review and commit**

Stage only files from this feature and commit with:

`feat(admin): 支持查看工作空间会话`
