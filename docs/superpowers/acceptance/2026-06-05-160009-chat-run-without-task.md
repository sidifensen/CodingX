# Acceptance Criteria: Chat Run Without Task

**Spec:** `docs/superpowers/specs/2026-06-05-160009-chat-run-without-task-design.md`
**Date:** 2026-06-05
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | Sending a cloud chat message creates a `chat_execution_run` but does not create or update `task`. | Logic | `ChatStreamExecutionService.dispatch` receives a non-local `SendChatMessageCommand`. | The service saves a `ChatExecutionRun` with `status=RUNNING`, `id=runId`, `taskId=runId` for compatibility, and no `TaskRepository` dependency or invocation exists in the service. |
| AC-002 | Chat background execution still finishes with a durable run terminal state after `task` removal. | Logic | The asynchronous chat worker completes successfully, throws `ConflictException`, or throws a runtime exception. | Success relies on `ChatApplicationService.recordExecutionOutcome`; rejection writes `status=REJECTED`; failures write `status=ERROR`; all paths release the conversation run lock and mark the conversation completion reminder unread. |
| AC-003 | Conversation list active status is projected from `chat_execution_run` only. | Logic | A conversation has `lastRunId` and repository returns a latest run in running, completed, or failed state. | `ChatConversationViewService` returns compatible `activeTask*` and `lastTask*` fields from run data without querying `TaskRepository`. |
| AC-004 | Expert replay no longer reads or writes `task_expert`. | Logic | A run context step contains `metadata_json` with `expertCode`. | Current expert lookup and regenerate context use `ChatRunContextStepSupport.parseContext(...)`; `ChatExpertRepository` exposes no task binding methods. |
| AC-005 | Database baseline no longer defines `task` or `task_expert`. | Logic | `backend/src/main/resources/db/schema.sql` is parsed by the repository structure test. | The schema contains no `CREATE TABLE ... task`, no `CREATE TABLE ... task_expert`, and no indexes or comments for those tables. |
| AC-006 | Migration removes old tables after preserving expert context. | Logic | A database has legacy `task_expert` rows and matching chat execution runs. | The new migration upserts missing `expertCode` into `chat_execution_step` context rows, then drops `task_expert` and `task`. |
| AC-007 | Public task APIs are removed with their backend module. | API | Backend source and tests are searched for `/api/tasks`, `TaskController`, `TaskRepository`, and `TaskResponse`. | No production controller or service exposes task endpoints or task repository abstractions after the refactor. |
| AC-008 | Chat stream meta remains frontend-compatible while exposing run semantics. | API | `/api/chat/stream` starts a non-local chat run. | The initial meta payload includes `runId` and retains `taskId` with the same value for compatibility. |
| AC-009 | Backend validation passes after the migration. | Logic | The implementation is complete. | `cd backend && mvn compile` and `cd backend && mvn test` both complete successfully, unless unrelated pre-existing changes are identified as the cause of failure. |
