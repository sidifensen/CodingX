# 管理端意图树 ragent 对齐 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 CodingX 管理端意图树升级为接近 `D:\code\ragent` 的树形配置台，并同步扩展后端契约与数据库结构。

**Architecture:** 后端保留 `/api/admin/chat/intents` 作为管理端入口，在现有领域模型上补齐 ragent 字段并新增树形视图能力。前端复用当前管理端设计令牌，重构单页为左树右详情和自定义弹窗表单。

**Tech Stack:** Spring Boot 3.4、MyBatis-Plus、Hutool、PostgreSQL、React 19、VitePlus、Tailwind CSS、Vitest、Testing Library。

---

### Task 1: 后端契约测试

**Files:**
- Create: `backend/src/test/java/com/codingx/chat/application/service/AdminChatIntentServiceTest.java`
- Create: `backend/src/test/java/com/codingx/chat/interfaces/controller/AdminChatIntentControllerTest.java`
- Modify after RED: `backend/src/main/java/com/codingx/chat/application/service/AdminChatIntentService.java`
- Modify after RED: `backend/src/main/java/com/codingx/chat/interfaces/controller/AdminChatIntentController.java`

- [ ] **Step 1: Write failing service tests**

覆盖 `listTreeBuildsChildren`、`saveDerivesIntentTypeFromKind`、`saveDerivesKindFromIntentType`、`saveRejectsDuplicateIntentCode`、`deleteRejectsNodeWithChildren`。

- [ ] **Step 2: Run service tests to verify RED**

Run: `cd backend && mvn -Dtest=AdminChatIntentServiceTest test`

Expected: FAIL because tree/delete/derive methods and repository methods are not implemented.

- [ ] **Step 3: Write failing controller tests**

覆盖 `GET /api/admin/chat/intents/tree`、`POST /api/admin/chat/intents`、`PUT /api/admin/chat/intents/{id}`、`DELETE /api/admin/chat/intents/{id}`。

- [ ] **Step 4: Run controller tests to verify RED**

Run: `cd backend && mvn -Dtest=AdminChatIntentControllerTest test`

Expected: FAIL because routes and response DTOs are incomplete.

### Task 2: 后端模型、仓储与接口实现

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/domain/model/ChatIntentNode.java`
- Modify: `backend/src/main/java/com/codingx/chat/domain/repository/ChatIntentNodeRepository.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/dataobject/ChatIntentNodeDO.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/repository/ChatIntentNodeRepositoryImpl.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/AdminChatIntentService.java`
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/controller/AdminChatIntentController.java`

- [ ] **Step 1: Implement minimal backend code**

Add ragent fields, `findById`, `existsByIntentCode`, `hasChildren`, tree assembly, create/update/delete methods, and `kind`/`intentType` normalization.

- [ ] **Step 2: Run targeted tests**

Run: `cd backend && mvn -Dtest=AdminChatIntentServiceTest,AdminChatIntentControllerTest test`

Expected: PASS.

- [ ] **Step 3: Refactor while green**

Keep conversion helpers private and documented with concise comments where field compatibility is non-obvious.

### Task 3: 数据库迁移与种子数据

**Files:**
- Create: `backend/src/main/resources/db/migration/V20260516_020500__extend_chat_intent_node_for_admin_tree.sql`
- Modify: `backend/src/main/resources/db/schema.sql`
- Modify: `backend/src/main/resources/db/init.sql`
- Modify: `backend/src/test/java/com/codingx/chat/infrastructure/persistence/repository/ChatRuntimePersistenceStructureTest.java`

- [ ] **Step 1: Write failing schema assertion**

Extend persistence structure test to assert new `ChatIntentNodeDO` fields exist.

- [ ] **Step 2: Run schema test to verify RED**

Run: `cd backend && mvn -Dtest=ChatRuntimePersistenceStructureTest test`

Expected: FAIL before DO/schema updates are complete.

- [ ] **Step 3: Add migration and baseline schema**

Add new columns, Chinese comments, backfill `kind`/`level`/`sort_order`, and update seed insert/update columns.

- [ ] **Step 4: Run schema test to verify GREEN**

Run: `cd backend && mvn -Dtest=ChatRuntimePersistenceStructureTest test`

Expected: PASS.

### Task 4: 前端页面测试

**Files:**
- Create: `frontend/admin/src/pages/IntentTreePage.test.tsx`
- Modify after RED: `frontend/admin/src/api/adminChatApi.ts`
- Modify after RED: `frontend/admin/src/pages/IntentTreePage.tsx`

- [ ] **Step 1: Write failing UI tests**

Mock `AdminChatApi` and cover tree render, node selection, edit dialog open, create dialog MCP validation.

- [ ] **Step 2: Run frontend tests to verify RED**

Run: `cd frontend/admin && npm run test:run -- IntentTreePage.test.tsx`

Expected: FAIL because current page has no tree/detail/dialog structure.

### Task 5: 前端 API 和页面实现

**Files:**
- Modify: `frontend/admin/src/api/adminChatApi.ts`
- Replace: `frontend/admin/src/pages/IntentTreePage.tsx`

- [ ] **Step 1: Implement API methods**

Add `getIntentTree`、`createIntent`、`updateIntent`、`deleteIntent` while keeping `listIntents` and `saveIntent` compatible.

- [ ] **Step 2: Implement page**

Build responsive left tree/right details layout, custom modal form, delete confirmation overlay, badge helpers, example parser, and dark-mode-safe styles using current tokens.

- [ ] **Step 3: Run targeted UI tests**

Run: `cd frontend/admin && npm run test:run -- IntentTreePage.test.tsx`

Expected: PASS.

### Task 6: Full Verification

**Files:**
- No direct edits unless verification finds defects.

- [ ] **Step 1: Backend verification**

Run: `cd backend && mvn compile`

Expected: BUILD SUCCESS.

Run: `cd backend && mvn test`

Expected: BUILD SUCCESS.

- [ ] **Step 2: Frontend verification**

Run: `cd frontend/admin && npm run build`

Expected: successful build.

Run: `cd frontend/admin && npm run test:run`

Expected: all tests pass.

- [ ] **Step 3: Browser verification**

Start backend on `http://localhost:5001` and admin frontend on `http://localhost:5003`, then use CDP at `/intent-tree`. Save screenshot to `logs/admin-intent-tree-ragent-parity.png` and verify card/modal backgrounds are non-transparent in light and dark mode.

### Task 7: Review and Commit

**Files:**
- All changed files from this plan.

- [ ] **Step 1: Self review**

Check API compatibility, dark mode, no native browser dialogs, database comments, and necessary comments in touched files.

- [ ] **Step 2: Commit**

Run: `git add <changed files>`

Run: `git commit -m "feat: 对齐管理端意图树配置台"`

Expected: commit succeeds with Chinese message and allowed prefix.
