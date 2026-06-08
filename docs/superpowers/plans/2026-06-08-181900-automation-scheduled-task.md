# Automation Scheduled Task Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build persistent scheduled automation tasks with manual creation on the automation page and direct chat-created tasks that reply inside the current conversation.

**Architecture:** Add a focused backend `automation` module with task model, repository, service, controller, parser and scheduler. Integrate chat creation through a small optional service call in `ChatApplicationService` before the normal model route. Replace the static user automation page with an API-backed task manager and custom create modal.

**Tech Stack:** Spring Boot 3.4, MyBatis-Plus, Hutool, Sa-Token, React 19, TypeScript, Vitest, Testing Library, lucide-react.

---

### Task 1: Backend Automation Domain And Persistence

**Files:**
- Create: `backend/src/main/java/com/codingx/automation/domain/model/AutomationTask.java`
- Create: `backend/src/main/java/com/codingx/automation/domain/model/AutomationScheduleType.java`
- Create: `backend/src/main/java/com/codingx/automation/domain/model/AutomationTaskSourceType.java`
- Create: `backend/src/main/java/com/codingx/automation/domain/repository/AutomationTaskRepository.java`
- Create: `backend/src/main/java/com/codingx/automation/infrastructure/persistence/dataobject/AutomationTaskDO.java`
- Create: `backend/src/main/java/com/codingx/automation/infrastructure/persistence/mapper/AutomationTaskMapper.java`
- Create: `backend/src/main/java/com/codingx/automation/infrastructure/persistence/repository/AutomationTaskRepositoryImpl.java`
- Create: `backend/src/test/java/com/codingx/automation/application/service/AutomationTaskServiceTest.java`
- Modify: `backend/src/main/resources/db/schema.sql`
- Add migration: `backend/src/main/resources/db/migration/V20260608_181900__create_automation_task_table.sql`

- [x] **Step 1: Write failing tests**

Create `AutomationTaskServiceTest` with tests for manual create, user-only list filtering, invalid time rejection and due task triggering.

- [x] **Step 2: Verify RED**

Run: `cd backend; mvn -Dtest=AutomationTaskServiceTest test`

Expected: compilation fails because `AutomationTaskService` and automation domain classes do not exist.

- [x] **Step 3: Implement domain, repository, service and SQL**

Add `automation_task` table and Java mapping. Use Hutool for string blank checks where useful. Include required class, field and method comments.

- [x] **Step 4: Verify GREEN**

Run: `cd backend; mvn -Dtest=AutomationTaskServiceTest test`

Expected: tests pass.

### Task 2: Backend API And Chat Creation Integration

**Files:**
- Create: `backend/src/main/java/com/codingx/automation/application/service/AutomationTaskService.java`
- Create: `backend/src/main/java/com/codingx/automation/application/service/AutomationTaskIntentParser.java`
- Create: `backend/src/main/java/com/codingx/automation/application/service/AutomationTaskChatCreationService.java`
- Create: `backend/src/main/java/com/codingx/automation/interfaces/controller/AutomationTaskController.java`
- Create request/response DTOs under `backend/src/main/java/com/codingx/automation/interfaces`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationServiceTest.java`

- [x] **Step 1: Write failing chat and controller tests**

Add tests proving explicit daily chat text creates a task and saves assistant summary, while normal chat does not create automation.

- [x] **Step 2: Verify RED**

Run: `cd backend; mvn -Dtest=ChatApplicationServiceTest,AutomationTaskControllerTest test`

Expected: tests fail because chat integration and controller are missing.

- [x] **Step 3: Implement parser, chat creation service, controller and chat branch**

Parser handles conservative `DAILY`, `WEEKLY`, and `ONCE` patterns. Controller only adapts protocol. Chat branch returns early only after successful task creation and assistant message persistence.

- [x] **Step 4: Verify GREEN**

Run: `cd backend; mvn -Dtest=ChatApplicationServiceTest,AutomationTaskControllerTest test`

Expected: tests pass.

### Task 3: Frontend Automation Page

**Files:**
- Create: `frontend/user/src/api/automationApi.ts`
- Create: `frontend/user/src/views/automation/types.ts`
- Rewrite: `frontend/user/src/views/AutomationView.tsx`
- Create/modify tests: `frontend/user/tests/views/AutomationView.test.tsx`

- [x] **Step 1: Write failing UI tests**

Cover empty state, create modal, disabled save, successful API save and backend error display.

- [x] **Step 2: Verify RED**

Run: `cd frontend/user; npm run test:run -- AutomationView.test.tsx`

Expected: tests fail because API-backed UI does not exist.

- [x] **Step 3: Implement API and page**

Build a compact work-focused page matching the reference: title “定时任务”, create button, empty state, custom modal, task list. Use existing theme tokens and no native browser dialogs.

- [x] **Step 4: Verify GREEN**

Run: `cd frontend/user; npm run test:run -- AutomationView.test.tsx`

Expected: tests pass.

### Task 4: Documentation, Full Verification, Browser Evidence

**Files:**
- Create: `docs/features/automation/scheduled-tasks.md`
- Modify: `docs/features/index.md`
- Update memory docs if implementation changes stable contracts.

- [x] **Step 1: Write feature doc**

Document purpose, entry points, core flow, key files, data structure and verification.

- [x] **Step 2: Run backend verification**

Run: `cd backend; mvn compile; mvn test`

Expected: both pass unless unrelated dirty-worktree changes cause failures; if unrelated failures occur, stop and report.

- [x] **Step 3: Run frontend verification**

Run: `cd frontend/user; npm run build; npm run test:run`

Expected: both pass unless unrelated dirty-worktree changes cause failures.

- [x] **Step 4: Browser verification**

Start required services on available ports after checking port occupancy. Open `/automation` through `/web-access`/CDP, capture screenshot in `logs/`, and verify modal background/text computed styles in dark mode. Evidence: `logs/automation-dark-create-dialog.png`.

- [ ] **Step 5: Review and commit**

Run code review checks, stage only files changed for this feature, and commit with Chinese message `feat(automation): 增加定时任务手动与会话创建能力`.
