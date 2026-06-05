# Chat Run Without Task Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `task` and `task_expert` from the chat execution model while preserving background execution, stream resume, completion reminders, and expert replay.

**Architecture:** `chat_execution_run` becomes the authoritative runtime state for cloud chat runs. `chat_message` remains the only message body store, and `chat_execution_step` context metadata becomes the only persisted source for selected skills, MCPs, and experts. API fields with `Task` names are temporarily retained as compatibility projections sourced from run records.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, PostgreSQL/Flyway SQL migrations, JUnit 5/Mockito, React/Vitest for touched frontend compatibility if needed.

---

### Task 1: Documented Contract And Database Shape

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/infrastructure/persistence/repository/ChatRuntimePersistenceStructureTest.java`
- Modify: `backend/src/main/resources/db/schema.sql`
- Create: `backend/src/main/resources/db/migration/V20260605_160009__remove_task_tables_from_chat_runtime.sql`
- Modify: `docs/features/chat/background-stream-resume.md`
- Modify: `docs/features/chat/skill-context-persistence.md`
- Modify: `docs/superpowers/memory/chat/background-stream-resume-contract.md`

- [ ] **Step 1: Write the failing schema test**

Add assertions that `schema.sql` does not contain `CREATE TABLE IF NOT EXISTS task (` or `CREATE TABLE IF NOT EXISTS task_expert (` and that `chat_execution_run` still exists.

- [ ] **Step 2: Run the targeted test to verify RED**

Run: `cd backend; mvn -Dtest=ChatRuntimePersistenceStructureTest test`

Expected: fail because `schema.sql` still defines `task` and `task_expert`.

- [ ] **Step 3: Update schema and migration**

Remove `task` and `task_expert` table definitions, comments, and indexes from `schema.sql`. Add a Flyway migration that:

```sql
WITH legacy_expert_context AS (
    SELECT task_id AS run_id, max(expert_code) AS expert_code
    FROM task_expert
    WHERE expert_code IS NOT NULL AND btrim(expert_code) <> ''
    GROUP BY task_id
)
INSERT INTO chat_execution_step (
    id,
    run_id,
    step_type,
    step_name,
    status,
    content,
    metadata_json,
    started_at,
    finished_at,
    created_at
)
SELECT
    nextval('chat_execution_step_id_seq'),
    context.run_id,
    'context',
    'run-capability-context',
    'COMPLETED',
    '运行能力上下文',
    jsonb_build_object('expertCode', context.expert_code)::text,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM legacy_expert_context context
WHERE NOT EXISTS (
    SELECT 1
    FROM chat_execution_step step
    WHERE step.run_id = context.run_id
      AND step.step_type = 'context'
      AND step.step_name = 'run-capability-context'
);

DROP TABLE IF EXISTS task_expert;
DROP TABLE IF EXISTS task;
```

If the project uses explicit Snowflake IDs rather than a sequence for `chat_execution_step.id`, adjust the migration to update existing context rows only and document that new code writes all future expert context.

- [ ] **Step 4: Update feature and memory docs**

Replace statements saying `task` is authoritative with `chat_execution_run` authoritative state, and replace `task_expert` replay with `chat_execution_step` context replay.

- [ ] **Step 5: Run the targeted test to verify GREEN**

Run: `cd backend; mvn -Dtest=ChatRuntimePersistenceStructureTest test`

Expected: pass.

### Task 2: Chat Dispatch Without Task Repository

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatStreamExecutionServiceTest.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatStreamExecutionService.java`

- [ ] **Step 1: Write failing dispatch tests**

Update tests to assert non-local dispatch saves a `ChatExecutionRun(status=RUNNING, queueStatus=WAITING)`, starts the chat worker, and does not verify `TaskRepository` or `ChatExpertRepository.bindTaskExpert`.

- [ ] **Step 2: Run targeted RED**

Run: `cd backend; mvn -Dtest=ChatStreamExecutionServiceTest test`

Expected: fail because production code still requires and saves `TaskRepository`.

- [ ] **Step 3: Remove task/expert binding from dispatch**

Remove `TaskRepository`, `Task`, and `RuntimeType` dependencies from `ChatStreamExecutionService`. Rename method parameters/comments from task to run where practical. Keep compatibility by accepting the externally supplied ID and writing `ChatExecutionRun.taskId=runId`. Replace `markTaskFinished` with `markConversationRunFinished`, which only marks `chat_conversation.task_completion_read=0`.

- [ ] **Step 4: Run targeted GREEN**

Run: `cd backend; mvn -Dtest=ChatStreamExecutionServiceTest test`

Expected: pass.

### Task 3: Conversation Projection From Run Records

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatConversationViewServiceTest.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatConversationViewService.java`

- [ ] **Step 1: Write failing projection tests**

Cover a conversation whose latest run is `RUNNING`, `COMPLETED`, and `ERROR`. Assert compatible `activeTask*` and `lastTask*` fields are derived from run state without a task repository mock.

- [ ] **Step 2: Run targeted RED**

Run: `cd backend; mvn -Dtest=ChatConversationViewServiceTest test`

Expected: fail because service still requires `TaskRepository`.

- [ ] **Step 3: Remove task repository projection**

Remove `TaskRepository` from constructor and `resolveTaskProjection`. Use only `ChatExecutionRunRepository.findByConversationId`. Map `COMPLETED/SUCCESS` to `SUCCEEDED`, `RUNNING/WAITING/ACQUIRED` to `RUNNING`, `ERROR/FAILED/REJECTED/CANCELLED` to `FAILED`, and use `finishedAt` from run.

- [ ] **Step 4: Run targeted GREEN**

Run: `cd backend; mvn -Dtest=ChatConversationViewServiceTest test`

Expected: pass.

### Task 4: Expert Context Without task_expert

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatWorkspaceQueryServiceTest.java`
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationServiceTest.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatWorkspaceQueryService.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`
- Modify: `backend/src/main/java/com/codingx/expert/domain/repository/ChatExpertRepository.java`
- Modify: `backend/src/main/java/com/codingx/expert/infrastructure/persistence/repository/ChatExpertRepositoryImpl.java`
- Delete: `backend/src/main/java/com/codingx/expert/infrastructure/persistence/dataobject/TaskExpertDO.java`
- Delete: `backend/src/main/java/com/codingx/expert/infrastructure/persistence/mapper/TaskExpertMapper.java`

- [ ] **Step 1: Write failing expert replay tests**

Adjust tests so current expert and regenerate context are read from `ChatRunContextStepSupport.parseContext(chatExecutionStepRepository.findByRunId(runId)).expertCode()`.

- [ ] **Step 2: Run targeted RED**

Run: `cd backend; mvn -Dtest=ChatWorkspaceQueryServiceTest,ChatApplicationServiceTest test`

Expected: fail because repository interface still includes task expert binding and production code still writes it.

- [ ] **Step 3: Remove task expert repository API**

Delete `findByTaskId` and `bindTaskExpert` from `ChatExpertRepository` and implementation. Remove `TaskExpertDO` and `TaskExpertMapper`. Update application services to use `ChatRunContextStepSupport` exclusively.

- [ ] **Step 4: Run targeted GREEN**

Run: `cd backend; mvn -Dtest=ChatWorkspaceQueryServiceTest,ChatApplicationServiceTest test`

Expected: pass.

### Task 5: Remove Task Backend Module And Public API

**Files:**
- Delete: `backend/src/main/java/com/codingx/task/**`
- Delete: `backend/src/test/java/com/codingx/task/**`
- Modify or delete: `backend/src/main/java/com/codingx/runtime/infrastructure/executor/MockTaskRuntimeExecutor.java`
- Modify: any Spring references that still import `com.codingx.task`

- [ ] **Step 1: Write/search failing check**

Run `rg -n "com\\.codingx\\.task|/api/tasks|TaskRepository|TaskController|TaskResponse" backend/src/main/java backend/src/test/java`.

Expected before implementation: matches exist.

- [ ] **Step 2: Delete task module and unused runtime executor**

Remove production and test classes that only support `/api/tasks`. If `MockTaskRuntimeExecutor` is only task-backed, delete it and remove bean references; if another module depends on the runtime executor abstraction, replace task-specific code with a no-op or chat-run-specific abstraction only if tests require it.

- [ ] **Step 3: Re-run search**

Run `rg -n "com\\.codingx\\.task|/api/tasks|TaskRepository|TaskController|TaskResponse" backend/src/main/java backend/src/test/java`.

Expected: no matches in production/test Java.

### Task 6: Stream Meta Compatibility

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/interfaces/controller/ChatStreamControllerTest.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatStreamRequestApplicationService.java`

- [ ] **Step 1: Write failing meta test**

Assert stream meta payload includes `runId` and retains `taskId` equal to `runId`.

- [ ] **Step 2: Run targeted RED**

Run: `cd backend; mvn -Dtest=ChatStreamControllerTest test`

Expected: fail because current meta only exposes `taskId`.

- [ ] **Step 3: Add runId to meta**

Add `metaPayload.put("runId", runId)` and keep `taskId` for compatibility. Rename local variables where low risk.

- [ ] **Step 4: Run targeted GREEN**

Run: `cd backend; mvn -Dtest=ChatStreamControllerTest test`

Expected: pass.

### Task 7: Final Verification And Commit

**Files:**
- Modify: `docs/features/index.md` if feature docs are renamed or new docs are added.

- [ ] **Step 1: Run backend compile**

Run: `cd backend; mvn compile`

Expected: success.

- [ ] **Step 2: Run backend tests**

Run: `cd backend; mvn test`

Expected: success, unless unrelated pre-existing dirty worktree changes cause a failure; if so, stop and report the unrelated failure.

- [ ] **Step 3: Review git diff for accidental unrelated edits**

Run: `git diff --stat` and `git diff --name-only`.

Expected: only task/run migration files and required docs/tests are changed by this task.

- [ ] **Step 4: Commit**

Stage only this task's files and commit with:

```bash
git commit -m "refactor(chat): 移除聊天任务表运行状态依赖"
```
