# Local Chat Local-Only Design

**Date:** 2026-05-26
**Status:** Approved

## Goal

Local runtime mode must not create cloud database workspace, conversation, or message records by default. Selecting a local folder only establishes a local execution context for the current desktop session and a local history partition on the client.

## Current Behavior

Desktop folder binding calls `/api/chat/workspace/bind-repository`. The backend validates the path, creates or reuses a `workspace` row with `runtime_target = local`, and returns `workspaceId`. Frontend local chat then sends that `workspaceId` to `/api/chat/stream`, so the backend creates `chat_conversation` and `chat_message` rows for local project conversations.

This makes local project history appear as cloud database data even when the user only selected a local folder.

## Target Behavior

Local folder binding validates the path and records the path in process memory for tool execution, but it does not create or return a database workspace id. Frontend local mode uses `local::<normalized path>` local snapshots as the source of truth.

When a local chat starts, the stream request marks itself as local-only and sends the selected repository path plus local message context. The backend may run model and tool execution, but it must not create `workspace`, `chat_conversation`, or `chat_message` rows. The SSE meta event returns a client-stable local conversation id supplied by the frontend or generated with a `local-` prefix. Frontend persists the final replay to local storage.

Cloud mode remains unchanged: cloud chats create or reuse default cloud workspace records and persist conversations/messages in the database.

## Data Boundaries

- `workspace` table stores cloud workspaces only by default. Existing `runtime_target = local` rows are treated as legacy data and should not be created by new local folder selection.
- Local project path remains on the desktop client and backend process memory during the active session. The stream request may include `repositoryPath` only to execute local tools for that request.
- Local chat messages are persisted in `codingx.chat.workspace.conversations.v1` on the client until a future explicit sync feature exists.
- Attachments, sharing, feedback, and admin workspace conversation views remain cloud-only unless explicitly redesigned later.

## Implementation Boundaries

- Modify `ChatWorkspaceBindingService` and `ChatWorkspaceBindingController` so binding no longer inserts workspace rows and can return a null workspace id.
- Modify frontend binding types so local runtime accepts null workspace id.
- Modify stream URL construction to send `runtimeTarget=local` and omit `workspaceId` for local mode.
- Add a local-only path in `ChatStreamController` and `ChatStreamExecutionService` that uses a transient conversation id and avoids database conversation/message writes.
- Preserve cloud behavior and existing default cloud history semantics.

## Testing

- Backend unit tests verify local binding does not call `workspaceMapper.insert` and returns no workspace id.
- Backend stream/controller tests verify local-only stream does not create a database conversation when no conversation id is supplied.
- Frontend hook tests verify selecting a local folder does not persist or pass a cloud workspace id, and local stream URLs contain `runtimeTarget=local` without `workspaceId`.
- Existing cloud tests continue to pass.
