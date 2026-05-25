# Chat Background Task Design

**Date:** 2026-05-25
**Status:** Approved

## Goal

All chat sessions behave as durable backend tasks. A chat task must continue running after the user switches sessions, refreshes the page, or disconnects the current browser tab. The session list shows a running icon instead of the relative time while a task is active, then restores the time and shows a completion dot when a background task finishes outside the currently open session. Opening the session clears that dot.

## Architecture

The backend is the source of truth for task execution. Chat sending creates one task row and one chat execution run that share the same identifier. The stream entry publishes that task identifier to the client, dispatches the work on the existing chat executor, and returns immediately with an SSE connection for the active session. If the client disconnects, the executor continues and persists task/run status.

The frontend treats the session list as a task dashboard. It hydrates status from the conversation list response, maintains local unread-completion state in the existing workspace snapshot, and only attaches the live chat stream to the currently selected session. Switching sessions does not cancel any task.

## Backend Behavior

- `ChatStreamController.streamChat` resolves or creates the conversation, creates a single backend task identifier, publishes it in the `meta` event, and passes it into the dispatcher.
- `ChatStreamExecutionService` persists a `Task` with `RUNNING` lifecycle and a `ChatExecutionRun` with the same `taskId`.
- Completion, cancellation, rejection, and failure paths update both `task` and `chat_execution_run`.
- `ChatConversationResponse` includes task projection fields derived from the latest execution run and task row:
  - `activeTaskId`
  - `activeTaskStatus`
  - `lastTaskId`
  - `lastTaskStatus`
  - `lastTaskFinishedAt`
- A task is active when its task status is `RUNNING` or its latest run status is `RUNNING`, `WAITING`, or `ACQUIRED`.
- Existing message, step, reference, artifact, current-skill, current-MCP, and current-expert replay endpoints remain conversation based.

## Frontend Behavior

- `ConversationItem` carries backend task status fields plus a local `hasUnreadTaskCompletion` flag.
- `useChatWorkspace` merges task status from API responses and stream events into the current conversation list and workspace groups.
- Sending a message immediately marks the target conversation as running using the task id from stream `meta`.
- When a background task changes from running to terminal state and the conversation is not currently active, the frontend sets `hasUnreadTaskCompletion`.
- Opening a conversation clears `hasUnreadTaskCompletion` for that conversation and persists the seen `lastTaskFinishedAt` in the workspace snapshot.
- Sidebar session rows use the existing right-side slot:
  - running: show a small animated loader and hide the relative time
  - completed unread: show the relative time and a small dot
  - hover/menu: keep the menu trigger behavior and avoid overlap with status indicators

## Persistence

The unread-completion dot is frontend-local because it is a personal notification state. It is stored in the existing workspace snapshot alongside conversation records as the latest seen task completion timestamp per conversation. On refresh, a conversation is unread when `lastTaskFinishedAt` is newer than the stored seen timestamp and the conversation is not currently open.

## Error Handling

Backend task failures update `TaskStatus.FAILED`, write the error message to `task.error_message`, and keep returning a generic chat error message through the existing global exception and stream error path. The conversation list must still leave the active-running state and show the completion dot for finished failed tasks so the user knows a background task ended.

## UI Constraints

The sidebar must keep its current compact operational style. Status icons and dots reuse theme tokens, work in light and dark modes, and do not introduce browser native dialogs. The right-side slot must remain stable in width so time, loader, dot, and menu trigger do not shift the title.

## Testing Strategy

- Backend controller and service tests verify task id consistency, task lifecycle updates, and conversation-list task fields.
- Frontend hook and sidebar tests verify running icon replacement, completion dot behavior, open-to-clear behavior, and refresh hydration from persisted seen timestamps.
- Final verification runs backend compile/test and frontend build/test because the feature touches both sides.
