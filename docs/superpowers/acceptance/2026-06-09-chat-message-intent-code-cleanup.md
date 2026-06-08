# Acceptance Criteria: Chat Message Intent Code Cleanup

**Spec:** `docs/superpowers/specs/2026-06-09-chat-message-intent-code-cleanup-design.md`
**Date:** 2026-06-09
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | `chat_message` must not keep the legacy message-level `intent_code` column in the active database. | API | PostgreSQL MCP can query `information_schema.columns` for the project database. | Querying `table_name = 'chat_message'` and `column_name = 'intent_code'` returns zero rows. |
| AC-002 | Existing environments must have an idempotent migration that removes the legacy message-level intent column. | Logic | Repository migration directory is available. | `V20260609_090000__drop_legacy_chat_message_intent_code.sql` exists and contains `ALTER TABLE chat_message` plus `DROP COLUMN IF EXISTS intent_code`. |
| AC-003 | New baseline schema and message DO mapping must not reintroduce the legacy message-level intent field. | Logic | Backend test sources can read `schema.sql` and reflect `ChatMessageDO`. | `schema.sql` has no `chat_message.intent_code` definition or comment, and `ChatMessageDO` has no `intentCode` field. |
| AC-004 | Run-level and intent-tree intent fields must remain untouched. | Logic | Repository SQL and code can be searched. | `chat_execution_run.intent_code` and `chat_intent_node.intent_code` remain present for run audit and intent tree business keys. |
