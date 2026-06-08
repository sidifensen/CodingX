# Hook Automation Design

## Background

Current Hook support only writes `governance_hook_audit` rows when a lifecycle point is reached. That does not match the intended product direction: Hook should be a configurable automation entry for task lifecycle events, desktop notifications, future pet integration, local scripts, or webhooks. Persistent Hook audit rows are not required for this scope.

## Goals

- Keep `governance_hook_rule` as the single database table for Hook configuration and management.
- Remove `governance_hook_audit` from schema, migration baseline, backend domain/repository code, admin API, admin UI, and tests.
- Built-in Hook rules must cover task start, task failure, permission confirmation, and task completion.
- Hook triggering must match enabled rules by `trigger_point` and optional `condition_keyword`, then return the matched rules for downstream automation dispatch.
- This change does not implement Windows notifications, pet rendering, local script execution, or webhook delivery. Those are future action executors that can consume matched Hook rules.

## Non-Goals

- Do not change `chat_conversation.task_completion_read`; sidebar completion reminders remain a chat runtime state, not a Hook responsibility.
- Do not add a replacement execution log table.
- Do not execute untrusted local scripts from the backend in this iteration.

## Data Model

`governance_hook_rule` remains unchanged structurally:

- `hook_code`: stable Hook identifier.
- `hook_name`: admin display name.
- `trigger_point`: lifecycle event such as `BEFORE_TASK_START`, `TASK_CONFIRM_REQUIRED`, `TASK_FAILED`, `TASK_COMPLETED`.
- `condition_keyword`: optional text filter against event context.
- `action_type`: future dispatcher key such as `DESKTOP_NOTIFY`, `PET_EVENT`, `LOCAL_SCRIPT`, or `WEBHOOK`.
- `action_config_json`: action-specific JSON payload template.
- `enabled`, `sort_no`, timestamps, and `deleted`: existing lifecycle fields.

`governance_hook_audit` is removed. Existing environments receive a migration that drops the table if present.

## Backend Flow

1. Chat runtime reaches a lifecycle point and calls `HookRuleService.trigger(...)` with `triggerPoint`, `conversationId`, `runId`, optional `toolCode`, and context text.
2. `HookRuleService` queries enabled rules for that trigger point, filters by `condition_keyword`, logs a lightweight application log line, and returns a list of matched `GovernanceHookRule` objects.
3. Current callers may ignore the returned list until a desktop notification or pet event dispatcher is implemented.
4. Permission policies continue to use `governance_permission_audit`; only Hook audit persistence is removed.

## Admin Flow

The admin Governance Center keeps the Hook Rules tab for listing, creating, editing, enabling, disabling, sorting, and deleting Hook rules. The audit tab only shows permission audits after this change. No admin endpoint fetches Hook audit records.

## Built-In Hooks

Seed data must include these enabled Hook rules:

- `before-task-start`: `BEFORE_TASK_START`
- `task-confirm-required`: `TASK_CONFIRM_REQUIRED`
- `task-failed`: `TASK_FAILED`
- `task-completed`: `TASK_COMPLETED`

Their first action type is `DESKTOP_NOTIFY`, with simple JSON config that future desktop code can interpret.

## Error Handling

Hook matching must not block the chat runtime. If rule lookup fails, `ChatApplicationService` keeps its current catch-and-log behavior around Hook triggering. Admin CRUD validation still returns Chinese `BusinessException` messages through the global `ApiResponse` handler.

## Testing

- Backend schema test verifies `governance_hook_audit` is absent and built-in Hook seed rows exist.
- Backend service test verifies `trigger(...)` returns matched enabled rules and does not require an audit repository.
- Admin API tests verify Hook audit API calls are removed and Hook rule APIs remain.
- Admin page tests or static build verify the page no longer loads or renders Hook audits.
