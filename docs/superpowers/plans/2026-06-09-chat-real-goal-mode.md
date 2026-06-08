# Chat Real Goal Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 CodingX 聊天目标模式升级为数据库持久化的会话级真实目标系统。

**Architecture:** 后端新增 `chat_goal`、`chat_goal_step`、`chat_goal_event` 三张表和目标应用服务，`get_goal/create_goal/update_goal` 通过当前聊天上下文读写数据库并发布 `goal` SSE。前端新增 `activeGoal` 状态和查询/流式更新链路，右侧浮窗只由 active goal 驱动，不再由 executionSteps 推导。

**Tech Stack:** Java 21、Spring Boot、MyBatis-Plus、PostgreSQL/H2 兼容 SQL、JUnit 5、React、TypeScript、Vitest、Testing Library、Tailwind 主题令牌。

---

### Task 1: Database Schema

**Files:**
- Create: `backend/src/main/resources/db/migration/V20260609_120000__create_chat_goal_tables.sql`
- Modify: `backend/src/main/resources/db/schema.sql`
- Test: `backend/src/test/java/com/codingx/chat/infrastructure/persistence/repository/ChatRuntimePersistenceStructureTest.java`

- [ ] **Step 1: Write failing structure test**

Add assertions that `schema.sql` and the new migration contain `chat_goal`、`chat_goal_step`、`chat_goal_event` with table comments and key column comments.

Run: `cd backend && mvn -Dtest=ChatRuntimePersistenceStructureTest test`

Expected: FAIL because the target tables do not exist yet.

- [ ] **Step 2: Add migration and baseline schema**

Create three tables with Chinese comments, indexes by conversation/goal/status, and soft delete columns. `chat_goal` must include conversation/user/goal_key/status/progress fields; `chat_goal_step` must include goal_id/step_key/status/sort_no; `chat_goal_event` must include goal_id/conversation_id/run_id/event_type/payload_json.

- [ ] **Step 3: Verify structure test**

Run: `cd backend && mvn -Dtest=ChatRuntimePersistenceStructureTest test`

Expected: PASS.

### Task 2: Backend Goal Domain and Service

**Files:**
- Create: `backend/src/main/java/com/codingx/chat/domain/model/ChatGoal.java`
- Create: `backend/src/main/java/com/codingx/chat/domain/model/ChatGoalStep.java`
- Create: `backend/src/main/java/com/codingx/chat/domain/model/ChatGoalStatus.java`
- Create: `backend/src/main/java/com/codingx/chat/domain/model/ChatGoalStepStatus.java`
- Create: `backend/src/main/java/com/codingx/chat/domain/repository/ChatGoalRepository.java`
- Create: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/dataobject/runtime/ChatGoalDO.java`
- Create: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/dataobject/runtime/ChatGoalStepDO.java`
- Create: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/dataobject/runtime/ChatGoalEventDO.java`
- Create: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/mapper/runtime/ChatGoalMapper.java`
- Create: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/mapper/runtime/ChatGoalStepMapper.java`
- Create: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/mapper/runtime/ChatGoalEventMapper.java`
- Create: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/repository/runtime/ChatGoalRepositoryImpl.java`
- Create: `backend/src/main/java/com/codingx/chat/application/service/goal/ChatGoalService.java`
- Create: `backend/src/main/java/com/codingx/chat/application/service/goal/ChatGoalView.java`
- Test: `backend/src/test/java/com/codingx/chat/application/service/ChatGoalServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Cover create, duplicate create, update with steps/events, and cross-conversation isolation.

Run: `cd backend && mvn -Dtest=ChatGoalServiceTest test`

Expected: FAIL because service and repository do not exist.

- [ ] **Step 2: Implement domain, repository, service**

Use Snowflake IDs, normalize statuses, bind by `conversationId/userId`, upsert steps by `step_key`, append event records for create/update. Add class, field, constructor, and method comments for all new/modified Java files.

- [ ] **Step 3: Verify service tests**

Run: `cd backend && mvn -Dtest=ChatGoalServiceTest test`

Expected: PASS.

### Task 3: Goal Tool Integration and SSE

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/domain/port/ChatStreamPublisher.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/stream/SseChatStreamPublisher.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/stream/NoopChatStreamPublisher.java`
- Modify: `backend/src/main/java/com/codingx/tool/application/service/CodexBuiltinChatToolExecutor.java`
- Modify: `backend/src/main/java/com/codingx/tool/application/service/ChatToolSpecService.java`
- Test: `backend/src/test/java/com/codingx/tool/application/service/CodexBuiltinChatToolExecutorTest.java`

- [ ] **Step 1: Write failing tool tests**

Cover context-required errors, database-backed create/get/update, and `goal` event publication.

Run: `cd backend && mvn -Dtest=CodexBuiltinChatToolExecutorTest -DfailIfNoTests=false test`

Expected: FAIL because goal tools still use in-memory map and no goal SSE port exists.

- [ ] **Step 2: Wire goal service into tool executor**

Inject `ChatGoalService`, remove `goals` map and private `GoalState`, parse tool input into service requests, and publish goal events after create/update. Extend model-visible tool list and schemas so `get_goal/create_goal/update_goal` are available to the model.

- [ ] **Step 3: Verify tool tests**

Run: `cd backend && mvn -Dtest=CodexBuiltinChatToolExecutorTest -DfailIfNoTests=false test`

Expected: PASS.

### Task 4: Goal Query API

**Files:**
- Create: `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatGoalController.java`
- Create: `backend/src/main/java/com/codingx/chat/interfaces/response/ChatGoalResponse.java`
- Test: `backend/src/test/java/com/codingx/chat/interfaces/controller/ChatGoalControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Verify `GET /api/chat/conversations/{conversationId}/goal/active` returns active goal for owner and null/no-content style response when absent.

Run: `cd backend && mvn -Dtest=ChatGoalControllerTest test`

Expected: FAIL because API does not exist.

- [ ] **Step 2: Implement controller and response mapping**

Controller only adapts HTTP/user identity, delegates to `ChatGoalService`, and returns `ApiResponse` with active goal or null data. Complex query logic stays in service/repository.

- [ ] **Step 3: Verify controller tests**

Run: `cd backend && mvn -Dtest=ChatGoalControllerTest test`

Expected: PASS.

### Task 5: Frontend Goal State and API

**Files:**
- Modify: `frontend/user/src/views/chat/types.ts`
- Modify: `frontend/user/src/views/chat/chatApi.ts`
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`
- Test: `frontend/user/tests/views/chat/chatApi.test.ts`
- Test: `frontend/user/tests/views/chat/useChatWorkspace.test.ts`

- [ ] **Step 1: Write failing frontend state tests**

Cover active goal API normalization, loading goal on conversation select, and applying `goal` SSE events.

Run: `cd frontend/user && npm run test:run -- tests/views/chat/chatApi.test.ts tests/views/chat/useChatWorkspace.test.ts -t "目标"`

Expected: FAIL because active goal types/API/state do not exist.

- [ ] **Step 2: Implement API and hook state**

Add `ChatGoalItem` and `ChatGoalStepItem`, `ChatApi.getActiveGoal`, `activeGoal` in controller, load it during conversation hydration, clear it on new conversation/no goal, and handle SSE `goal` events.

- [ ] **Step 3: Verify focused frontend logic**

Run: `cd frontend/user && npm run test:run -- tests/views/chat/chatApi.test.ts tests/views/chat/useChatWorkspace.test.ts -t "目标"`

Expected: PASS.

### Task 6: Frontend Goal Panel

**Files:**
- Modify: `frontend/user/src/views/ChatView.tsx`
- Test: `frontend/user/tests/views/ChatView.test.tsx`

- [ ] **Step 1: Write failing UI tests**

Update existing target mode tests so panel is hidden when only `goalModeEnabled=true` but no active goal, and visible when `activeGoal` exists.

Run: `cd frontend/user && npm run test:run -- tests/views/ChatView.test.tsx -t "目标模式"`

Expected: FAIL because panel still relies on old progress view.

- [ ] **Step 2: Implement active-goal panel**

Replace `buildGoalProgressView(executionSteps, isStreaming)` with `buildGoalProgressView(activeGoal)`. Show title, status label, completed/total, summary, and steps. Use existing theme tokens and avoid card nesting.

- [ ] **Step 3: Verify UI tests**

Run: `cd frontend/user && npm run test:run -- tests/views/ChatView.test.tsx -t "目标模式"`

Expected: PASS.

### Task 7: Documentation and Verification

**Files:**
- Modify: `docs/features/chat/desktop-goal-mode.md`
- Modify: `docs/features/index.md`
- Optional create: `docs/superpowers/memory/chat/real-goal-mode-contract.md`

- [ ] **Step 1: Update feature docs**

Document storage tables, tool contract, SSE event, active-goal panel behavior, and verification commands.

- [ ] **Step 2: Run backend verification**

Run: `cd backend && mvn compile` and `cd backend && mvn test`.

Expected: exit 0, or report unrelated pre-existing failures without editing other-session files.

- [ ] **Step 3: Run frontend verification**

Run: `cd frontend/user && npm run build` and `cd frontend/user && npm run test:run`.

Expected: exit 0, or report unrelated pre-existing failures without editing other-session files.

- [ ] **Step 4: Browser verification**

Check ports 5001 and 5002, start services if needed, open `http://localhost:5002` with CDP, verify active-goal panel display and dark/light readability, save evidence under `logs/`.

- [ ] **Step 5: Review and commit**

Stage only files touched for this feature and commit with `feat(chat): 实现真实目标模式持久化跟进`.
