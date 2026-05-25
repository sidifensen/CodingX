# Acceptance Criteria: Chat Background Task

**Spec:** `docs/superpowers/specs/2026-05-25-130500-chat-background-task-design.md`
**Date:** 2026-05-25
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | Sending a chat message creates a durable backend task whose id matches the stream meta task id and the chat execution run task id. | API | Authenticated user starts `/api/chat/stream` for an existing or new conversation. | The first stream meta payload contains `taskId`, the latest `chat_execution_run.task_id` equals that id, and the `task` row with that id is `RUNNING` while execution is active. |
| AC-002 | Chat execution continues after the client disconnects from the SSE connection. | API | A stream request has been accepted and the HTTP client closes the SSE connection before completion. | The backend executor still persists a terminal `task.status` and terminal `chat_execution_run.status`; the conversation's latest assistant message is available through the message list endpoint. |
| AC-003 | Conversation list items expose active task status for running sessions. | API | A conversation has a latest task/run in running state. | `/api/chat/conversations` returns that conversation with `activeTaskId` and `activeTaskStatus=RUNNING`; `lastTaskId` equals the latest task id. |
| AC-004 | Conversation list items expose terminal task metadata after completion. | API | A conversation latest task has finished successfully or failed. | `/api/chat/conversations` returns no active task id for that conversation, includes `lastTaskId`, `lastTaskStatus` as `SUCCEEDED` or `FAILED`, and includes `lastTaskFinishedAt`. |
| AC-005 | The sidebar replaces the relative time with a running icon for active background tasks. | UI interaction | Frontend receives a conversation item with `activeTaskStatus=RUNNING`. | The row right side contains an accessible running indicator, the relative time text for that row is hidden, and the title width remains stable. |
| AC-006 | A background task completion on an inactive conversation shows an unread completion dot. | UI interaction | Active conversation is `A`; conversation `B` changes from running to terminal state with a newer `lastTaskFinishedAt`. | Conversation `B` shows its relative time and a completion dot in the right-side slot. |
| AC-007 | Opening a conversation clears its completion dot. | UI interaction | A conversation has `hasUnreadTaskCompletion=true`. | Selecting that conversation removes the dot and persists the seen completion timestamp. |
| AC-008 | Refreshing the app restores running indicators and unread completion dots from backend status and local seen timestamps. | Logic | Workspace snapshot contains seen timestamps and the conversation list API returns task status fields. | Running conversations display running indicators; completed conversations only show dots when `lastTaskFinishedAt` is newer than the stored seen timestamp. |
| AC-009 | Cancelling or failing a task leaves the sidebar running state and remains inspectable. | API | A chat task is cancelled or fails during execution. | The task row becomes terminal, the execution run is terminal, `/api/chat/conversations` does not expose it as active, and message replay endpoints still return available persisted data. |
| AC-010 | Sidebar task indicators support light and dark themes without hard-coded one-off colors. | UI interaction | App is rendered under existing light and dark theme classes. | Loader, dot, hover state, and menu trigger use theme tokens or existing semantic classes and remain visually distinguishable in both themes. |

## Coverage Check

- Backend durability is covered by AC-001 through AC-004 and AC-009.
- Frontend list behavior is covered by AC-005 through AC-008 and AC-010.
- Completion-dot clearing is explicitly covered by AC-007.
- Refresh and disconnect recovery are covered by AC-002 and AC-008.
