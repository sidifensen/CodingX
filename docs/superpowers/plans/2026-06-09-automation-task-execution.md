# Automation Task Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make claimed automation tasks execute their prompt through the existing chat/search/model pipeline and deliver results to a conversation.

**Architecture:** Keep scheduling and execution separate. `AutomationTaskScheduler` scans and claims due tasks, while a new `AutomationTaskExecutionService` resolves or creates the delivery conversation and dispatches a background chat run through `ChatStreamExecutionService`.

**Tech Stack:** Spring Boot, JUnit 5, Mockito, Hutool ID utility, existing chat domain repositories and stream dispatch service.

---

### Task 1: Scheduler Delegation

**Files:**
- Modify: `backend/src/main/java/com/codingx/automation/infrastructure/scheduler/AutomationTaskScheduler.java`
- Test: `backend/src/test/java/com/codingx/automation/infrastructure/scheduler/AutomationTaskSchedulerTest.java`

- [ ] **Step 1: Write failing scheduler test**

Add a mocked `AutomationTaskExecutionService`, make `triggerDueTasks` return a task, call `scanDueTasks`, and verify `executeTriggeredTasks` receives that task.

- [ ] **Step 2: Run test to verify RED**

Run: `mvn -Dtest=AutomationTaskSchedulerTest test`
Expected: compilation or verification failure because the scheduler does not inject or call the execution service.

- [ ] **Step 3: Implement scheduler delegation**

Inject `AutomationTaskExecutionService` into `AutomationTaskScheduler`. After `triggerDueTasks(now)`, if the list is non-empty, call `automationTaskExecutionService.executeTriggeredTasks(triggeredTasks)`.

- [ ] **Step 4: Run test to verify GREEN**

Run: `mvn -Dtest=AutomationTaskSchedulerTest test`
Expected: PASS.

### Task 2: Execution Service

**Files:**
- Create: `backend/src/main/java/com/codingx/automation/application/service/AutomationTaskExecutionService.java`
- Test: `backend/src/test/java/com/codingx/automation/application/service/AutomationTaskExecutionServiceTest.java`
- Modify: `backend/src/main/java/com/codingx/automation/domain/repository/AutomationTaskRepository.java`
- Modify: `backend/src/test/java/com/codingx/automation/application/service/AutomationTaskServiceTest.java`

- [ ] **Step 1: Write failing execution tests**

Cover chat-source dispatch, manual delivery conversation creation, and invalid conversation failure. Use mocked repositories and `ChatStreamExecutionService`.

- [ ] **Step 2: Run tests to verify RED**

Run: `mvn -Dtest=AutomationTaskExecutionServiceTest test`
Expected: compilation failure because the service does not exist.

- [ ] **Step 3: Implement service and repository support**

Add `AutomationTaskExecutionService` with methods `executeTriggeredTasks(List<AutomationTask>)` and internal `executeTriggeredTask(AutomationTask)`. Use `ChatConversationRepository.findById`, `ChatConversation.create`, `AutomationTaskRepository.save`, and `ChatStreamExecutionService.dispatch`.

- [ ] **Step 4: Run tests to verify GREEN**

Run: `mvn -Dtest=AutomationTaskExecutionServiceTest,AutomationTaskSchedulerTest,AutomationTaskServiceTest test`
Expected: PASS.

### Task 3: Documentation and Verification

**Files:**
- Modify: `docs/features/automation/scheduled-tasks.md`
- Modify: `docs/superpowers/memory/automation/automation-scheduler-contract.md`
- Modify: `docs/superpowers/memory/automation/automation-scheduler-module-card.md`

- [ ] **Step 1: Update docs**

Replace statements saying execution is not connected with the new execution behavior and clarify that delivery occurs through source or generated chat conversations.

- [ ] **Step 2: Run verification**

Run: `mvn -Dtest=AutomationTaskExecutionServiceTest,AutomationTaskSchedulerTest,AutomationTaskServiceTest test`, `mvn compile`, and `git diff --check`.

- [ ] **Step 3: Commit**

Stage only automation execution files and docs. Commit with `feat(automation): 接入定时任务执行链路`.
