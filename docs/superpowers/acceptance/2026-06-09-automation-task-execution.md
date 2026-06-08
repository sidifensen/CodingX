# Acceptance Criteria: Automation Task Execution

**Spec:** `docs/superpowers/specs/2026-06-09-automation-task-execution-design.md`
**Date:** 2026-06-09
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | Due tasks claimed by the scheduler are handed to the automation execution service. | Logic | `AutomationTaskService.triggerDueTasks(now)` returns one claimed task. | `AutomationTaskExecutionService.executeTriggeredTasks(...)` is called once with that task. |
| AC-002 | A chat-created automation task executes in its source conversation. | Logic | Claimed task has `sourceConversationId` pointing to a conversation owned by `task.userId`. | `ChatStreamExecutionService.dispatch(runId, command, task.userId)` is called with `command.conversationId=sourceConversationId` and content containing the task prompt. |
| AC-003 | A manual task without a source conversation creates a delivery conversation before execution. | Logic | Claimed manual task has `sourceConversationId=null`. | A chat conversation titled `自动化任务：<任务名>` is saved for the task owner, the task is saved with that conversation ID, and dispatch uses the new conversation ID. |
| AC-004 | A task with an invalid source conversation is not dispatched. | Logic | Claimed task references a missing or foreign conversation. | No chat dispatch occurs, and the task is saved with `lastRunStatus=FAILED`. |
| AC-005 | Automation execution reuses existing chat delivery and notification behavior. | Logic | Execution service dispatches a non-local chat command. | The command uses `localOnly=false`, no attachment IDs, no selected skills/MCP by default, and the existing chat background service remains responsible for SSE completion and unread reminders. |
