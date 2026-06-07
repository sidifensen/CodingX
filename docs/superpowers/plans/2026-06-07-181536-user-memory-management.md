# User Memory Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a user-side memory management page with list, filter, edit, enable/disable, and delete support.

**Architecture:** Backend extends the existing user memory controller and service with content update and logical delete. Frontend adds a route-level `MemoryView`, extends `ChatApi`, and wires the page into `App` and `Sidebar`.

**Tech Stack:** Spring Boot, Sa-Token, MyBatis-Plus, Hutool, React 19, Vite+, Vitest, Tailwind CSS, lucide-react.

---

### Task 1: Backend User Memory Mutations

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/interfaces/controller/ChatMemoryControllerTest.java`
- Modify: `backend/src/test/java/com/codingx/governance/application/LongTermMemoryServiceTest.java`
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatMemoryController.java`
- Modify: `backend/src/main/java/com/codingx/governance/application/service/LongTermMemoryService.java`

- [ ] **Step 1: Write failing controller tests**

Add tests that call `PATCH /api/chat/memories/{id}` with `{"content":"新的记忆"}` and `DELETE /api/chat/memories/{id}`. Both tests mock `StpUtil.getLoginIdAsLong()` as `1002L` and verify the service receives `memoryId` and current user ID.

- [ ] **Step 2: Write failing service tests**

Add tests for `updateUserMemoryContent` and `deleteUserMemory`. Verify blank content throws, foreign user throws, edited content updates keyword JSON and updated time, deleted memory has `deleted=1`.

- [ ] **Step 3: Run backend RED tests**

Run:

```bash
cd backend && mvn -Dtest=ChatMemoryControllerTest,LongTermMemoryServiceTest test
```

Expected: FAIL because controller/service methods do not exist yet.

- [ ] **Step 4: Implement controller and service**

Add `@PatchMapping("/{memoryId}")` and `@DeleteMapping("/{memoryId}")` to `ChatMemoryController`. Add service methods that reuse ownership checks, normalize content, refresh `keywordJson`, rebuild `memoryKey`, update `updatedAt`, and logical delete with `deleted=1`.

- [ ] **Step 5: Run backend GREEN tests**

Run the same Maven command. Expected: PASS.

### Task 2: Frontend API and Page

**Files:**
- Modify: `frontend/user/tests/views/chat/chatApi.test.ts`
- Create: `frontend/user/tests/views/MemoryView.test.tsx`
- Modify: `frontend/user/src/views/chat/chatApi.ts`
- Create: `frontend/user/src/views/MemoryView.tsx`

- [ ] **Step 1: Write failing API tests**

Add tests for `ChatApi.updateLongTermMemoryContent` and `ChatApi.deleteLongTermMemory`. Verify URLs, HTTP methods, JSON body, and token headers.

- [ ] **Step 2: Write failing page tests**

Mock `ChatApi` and `AuthStorage`. Verify logged-in load, range/status filters, edit modal, disable/enable action, delete modal, and logged-out prompt.

- [ ] **Step 3: Run frontend RED tests**

Run:

```bash
cd frontend/user && npm run test:run -- tests/views/chat/chatApi.test.ts tests/views/MemoryView.test.tsx
```

Expected: FAIL because API methods and page do not exist yet.

- [ ] **Step 4: Implement API methods and `MemoryView`**

Use a dense list layout with segmented filters, custom edit/delete dialogs, and existing theme tokens. Do not use browser native dialogs.

- [ ] **Step 5: Run frontend GREEN tests**

Run the same Vitest command. Expected: PASS.

### Task 3: App Shell and Navigation

**Files:**
- Modify: `frontend/user/tests/App.test.tsx`
- Modify: `frontend/user/tests/components/Sidebar.test.tsx`
- Modify: `frontend/user/src/App.tsx`
- Modify: `frontend/user/src/components/Sidebar.tsx`

- [ ] **Step 1: Write failing navigation tests**

Verify `/memories` route renders the page and clicking “记忆管理” updates the path. Verify sidebar renders the new nav item.

- [ ] **Step 2: Run RED tests**

Run:

```bash
cd frontend/user && npm run test:run -- tests/App.test.tsx tests/components/Sidebar.test.tsx
```

Expected: FAIL before wiring the new route.

- [ ] **Step 3: Wire route and nav**

Add `memories` to `ViewType`, `VIEW_ROUTE_PATHS`, `Sidebar` nav items, and `AnimatePresence`. Pass auth state, login callback, workspace ID, and workspace label to `MemoryView`.

- [ ] **Step 4: Run GREEN tests**

Run the same command. Expected: PASS for route/sidebar coverage.

### Task 4: Docs and Verification

**Files:**
- Modify: `docs/features/index.md`
- Modify: `docs/features/governance/project-profile-long-term-memory.md`

- [ ] **Step 1: Update feature docs**

Document the new user memory management page, edit/delete endpoints, and validation commands.

- [ ] **Step 2: Run backend verification**

Run:

```bash
cd backend && mvn compile && mvn test
```

- [ ] **Step 3: Run frontend verification**

Run:

```bash
cd frontend/user && npm run build && npm run test:run
```

If existing unrelated tests fail, record exact failing tests and run focused passing tests for this feature.

- [ ] **Step 4: Browser verification**

Start backend/user frontend as needed, open `http://localhost:5002/memories`, verify dark theme readability and save screenshot under `logs/`.

- [ ] **Step 5: Commit**

Commit all remaining files with:

```bash
git add -A
git commit -m "feat(governance): 增加用户端记忆管理页"
```
