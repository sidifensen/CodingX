# Acceptance Criteria: Automation Scheduled Task

**Spec:** `docs/superpowers/specs/2026-06-08-181900-automation-scheduled-task-design.md`
**Date:** 2026-06-08
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | Manual creation persists an automation task with the logged-in user as owner. | API | User is authenticated and posts valid `name`, `prompt`, `scheduleType=DAILY`, and `scheduleTime=18:11` to `/api/automation/tasks`. | Response `success=true`; returned task has the same name/prompt, `sourceType=MANUAL`, `enabled=true`, and a non-null `nextRunAt`. |
| AC-002 | Automation list returns only current user's non-deleted tasks. | Logic | Repository contains tasks for two users and one deleted task. | `AutomationTaskService.listTasks(currentUserId)` returns only current user's non-deleted tasks sorted by enabled state and next run time. |
| AC-003 | Invalid manual schedule fields return a Chinese business error. | API | User posts blank name or invalid `scheduleTime` to `/api/automation/tasks`. | Response uses `ApiResponse` failure structure and `message` is a Chinese validation error from the backend. |
| AC-004 | Chat messages with explicit daily automation intent create a task directly in the current conversation. | Logic | Existing conversation belongs to the user; user sends “每天 18:11 帮我总结项目状态”. | `AutomationTaskChatCreationService` creates one `CHAT` task tied to the conversation and `ChatApplicationService` saves an assistant message containing “已创建自动化任务” and “18:11”. |
| AC-005 | Non-automation chat messages keep the normal chat route. | Logic | Existing conversation belongs to the user; user sends a normal question without schedule/create intent. | Automation creation service is not called or returns empty, and normal intent/model routing continues. |
| AC-006 | Chat-created tasks do not require navigating to the automation page for confirmation. | UI interaction | User sends an explicit automation creation request in chat. | Current chat message list shows the assistant task-created summary; browser URL remains on the chat route and no automation-page modal opens. |
| AC-007 | Scheduler triggers due enabled tasks idempotently and computes the next run. | Logic | A `DAILY` enabled task has `nextRunAt` before current time. | Scheduler/service marks it triggered once, sets `lastRunAt`, sets `lastRunStatus=TRIGGERED`, and advances `nextRunAt` to a future time. |
| AC-008 | Automation page displays reference-style empty state and opens a custom create modal. | UI interaction | Authenticated user has no automation tasks. | `/automation` shows the title “定时任务”, create button, empty state text, and clicking create opens an in-app modal with name, demand, schedule and time fields. |
| AC-009 | Automation page saves a manual task through the API and refreshes the list. | UI interaction | API mock returns a created task for valid form data. | After filling the modal and clicking save, the modal closes and the task row appears with name, prompt summary and schedule label. |
| AC-010 | Automation page supports dark mode with readable modal and non-transparent key containers. | UI interaction | App is in dark mode and `/automation` create modal is open. | CDP computed styles or screenshot show modal/container background is non-transparent and text/borders are readable. |
| AC-011 | Database migration and baseline schema define `automation_task` with Chinese comments. | Logic | Read SQL files under `backend/src/main/resources/db`. | Migration and `schema.sql` both contain `automation_task` table, all required columns, table comment, and column comments. |
| AC-012 | Feature documentation records the delivered automation behavior. | Logic | Implementation is complete. | `docs/features/index.md` links to an automation feature doc describing purpose, entry points, core flow, key files, data structure and verification. |
