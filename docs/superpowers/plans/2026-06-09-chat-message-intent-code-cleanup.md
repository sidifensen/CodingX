# Chat Message Intent Code Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the lingering `chat_message.intent_code` database column because message persistence no longer maps or reads it.

**Architecture:** Keep the existing domain and DO model unchanged because `ChatMessage` and `ChatMessageDO` already omit message-level intent. Add an idempotent migration that drops only `chat_message.intent_code`, keep `chat_execution_run.intent_code` untouched, and add a structure test that prevents the message column from returning.

**Tech Stack:** Spring Boot, MyBatis-Plus, PostgreSQL SQL migrations, JUnit 5.

---

### Task 1: Lock The Schema Contract

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/infrastructure/persistence/repository/ChatRuntimePersistenceStructureTest.java`

- [x] **Step 1: Write the failing structure test**

Add a test that reads `schema.sql`, reflects `ChatMessageDO`, and scans migration files. It must assert that `chat_message.intent_code` is absent from the schema and DO mapping while a latest idempotent migration explicitly drops the legacy column.

- [x] **Step 2: Attempt the RED verification**

Run: `mvn "-Dtest=com.codingx.chat.infrastructure.persistence.repository.ChatRuntimePersistenceStructureTest#chatMessageSchemaDoesNotContainLegacyIntentCode" test`

Expected before migration: FAIL with a message indicating the cleanup migration is missing. The initial RED attempt was blocked before this assertion by the then-current worktree test compilation state, so final evidence comes from the later GREEN run plus the staged migration check.

### Task 2: Add Cleanup Migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V20260609_090000__drop_legacy_chat_message_intent_code.sql`

- [x] **Step 1: Add idempotent migration**

Create a migration containing `ALTER TABLE chat_message DROP COLUMN IF EXISTS intent_code;`. This targets only the message table and intentionally leaves `chat_execution_run.intent_code` and `chat_intent_node.intent_code` intact.

- [x] **Step 2: Run the structure test to verify GREEN**

Run: `mvn "-Dtest=com.codingx.chat.infrastructure.persistence.repository.ChatRuntimePersistenceStructureTest#chatMessageSchemaDoesNotContainLegacyIntentCode" test`

Expected: PASS.

### Task 3: Document The Feature Contract

**Files:**
- Create: `docs/features/chat/message-intent-code-cleanup.md`
- Modify: `docs/features/index.md`

- [x] **Step 1: Document the current implementation**

Record that message-level intent is no longer stored on `chat_message`; run-level intent remains on `chat_execution_run.intent_code`.

- [x] **Step 2: Update feature index**

Add the new chat feature documentation link under the Chat section.

### Task 4: Verify And Commit

**Files:**
- Verify staged changes only include the migration, test, docs, and current DB cleanup if represented by migration.

- [x] **Step 1: Run backend compile**

Run: `mvn compile`

Expected: BUILD SUCCESS.

- [x] **Step 2: Run targeted backend tests**

Run: `mvn "-Dtest=com.codingx.chat.infrastructure.persistence.repository.ChatRuntimePersistenceStructureTest" test`

Expected: all tests in the class pass.

- [x] **Step 3: Verify actual database column was removed**

Run via PostgreSQL MCP: query `information_schema.columns` for `chat_message.intent_code`.

Expected: no rows.

- [x] **Step 4: Stage only task files and commit**

Commit message: `chore(chat): 清理消息表废弃意图字段`.
