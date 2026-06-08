# Chat Message Intent Code Cleanup Design

**Date:** 2026-06-09
**Status:** Approved

## Context

The current database still had a nullable `chat_message.intent_code` column. Static inspection showed the current baseline schema, `ChatMessageDO`, `ChatMessage`, and `ChatMessageRepositoryImpl` no longer map or use message-level intent. A previous migration already attempted to drop this column, but the active local database still retained it, so existing databases need an idempotent cleanup guard.

## Decision

Remove only `chat_message.intent_code`. Keep `chat_execution_run.intent_code` because run-level intent remains the correct audit location for intent decisions. Keep `chat_intent_node.intent_code` because it is the business key for the intent tree.

## Data Flow

New environments use `schema.sql`, whose `chat_message` definition does not include `intent_code`. Existing environments run a new idempotent migration that executes `ALTER TABLE chat_message DROP COLUMN IF EXISTS intent_code`. Runtime message persistence continues to map `ChatMessage` to `ChatMessageDO` without any intent field.

## Error Handling

The migration uses `DROP COLUMN IF EXISTS`, so environments where the field was already removed do not fail. No API behavior changes are required because the application does not read or write this column.

## Testing

Add a structure test that checks three constraints: `schema.sql` does not define or comment `chat_message.intent_code`, `ChatMessageDO` has no `intentCode` field, and the cleanup migration exists.
