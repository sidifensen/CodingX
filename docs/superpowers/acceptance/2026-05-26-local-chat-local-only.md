# Acceptance Criteria: Local Chat Local-Only

**Spec:** `docs/superpowers/specs/2026-05-26-221345-local-chat-local-only-design.md`
**Date:** 2026-05-26
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | Binding a desktop local folder validates and returns the normalized path without creating a workspace row. | API | A valid local directory is submitted to `/api/chat/workspace/bind-repository`. | The response contains `repositoryPath` and blank/null `workspaceId`, and the workspace mapper insert method is not called. |
| AC-002 | Switching to a local folder in the frontend stores the selected folder in the local snapshot without requiring a cloud workspace id. | Logic | `bindWorkspacePath` returns a normalized local path and no `workspaceId`. | `workspaceId` state remains null, `workspacePath` is set, and the local partition key is based on the folder path. |
| AC-003 | Local stream requests do not pass a database workspace id. | Logic | Active runtime target is `local`, a local folder is selected, and a message is submitted. | The stream URL contains `runtimeTarget=local` and `repositoryPath`, and it does not contain `workspaceId`. |
| AC-004 | Local-only stream startup does not create cloud conversation or message rows. | API | `/api/chat/stream` is called with `runtimeTarget=local`, a repository path, no database conversation id, and a local conversation id. | Backend dispatches a stream using the local conversation id and does not call cloud conversation creation for that request. |
| AC-005 | Cloud chat behavior is unchanged. | API | `/api/chat/stream` is called without `runtimeTarget=local`. | Backend creates or reuses the default cloud workspace and creates the cloud conversation exactly as before. |
| AC-006 | Existing local workspace rows are not shown in cloud default history. | Logic | Legacy local workspace/conversation rows exist in the database. | Default cloud history query excludes those rows unless an explicit workspace id is requested. |
