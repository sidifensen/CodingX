# Remove Code Search MCP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完整下线代码检索意图、内置 MCP 工具和运行时配置。

**Architecture:** 采用“迁移清理 + 基线移除 + 执行器删除 + 测试改约束”的方式处理。运行时不增加新的分支，删除后由现有工具缺失链路兜底历史引用。

**Tech Stack:** Spring Boot、MyBatis Plus、PostgreSQL SQL migration、Vitest、React 管理端。

---

### Task 1: 数据种子与迁移

**Files:**
- Create: `backend/src/main/resources/db/migration/V20260607_222030__remove_code_search_mcp_and_intents.sql`
- Modify: `backend/src/main/resources/db/init.sql`
- Modify: `backend/src/test/java/com/codingx/chat/infrastructure/persistence/repository/ChatIntentSeedScriptTest.java`

- [ ] **Step 1: Write the failing test**

更新 `ChatIntentSeedScriptTest.seedScriptsContainIntentSeedData`：

```java
String codeSearchCleanupMigrationSql = Files.readString(
    Path.of("src/main/resources/db/migration/V20260607_222030__remove_code_search_mcp_and_intents.sql")
);

for (String removedMarker : new String[] {"'sales'", "'sales-data'", "'ticket'", "'ticket-data'", "'code'", "'code-search'"}) {
    assertFalse(initSql.contains(removedMarker), "init.sql 不应再包含已下线意图: " + removedMarker);
}
assertFalse(initSql.contains("'code_search'"), "init.sql 不应再包含已下线 MCP: code_search");
assertTrue(codeSearchCleanupMigrationSql.contains("code-search"), "代码检索清理迁移必须覆盖历史叶子意图");
assertTrue(codeSearchCleanupMigrationSql.contains("code_search"), "代码检索清理迁移必须覆盖 MCP 与设置");
assertTrue(codeSearchCleanupMigrationSql.contains("DELETE FROM setting"), "代码检索清理迁移必须同步移除运行时设置");
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -Dtest=ChatIntentSeedScriptTest test`

Expected: FAIL because the cleanup migration does not exist and `init.sql` still contains code search seed data.

- [ ] **Step 3: Write migration and update init baseline**

Create migration:

```sql
-- 物理删除代码检索 MCP 配置、运行时设置与历史意图节点，防止已下线能力继续出现在管理端和运行时。
WITH RECURSIVE target AS (
    SELECT intent_code
    FROM chat_intent_node
    WHERE intent_code IN ('code', 'code-search')
    UNION ALL
    SELECT child.intent_code
    FROM chat_intent_node child
    JOIN target parent ON child.parent_code = parent.intent_code
)
DELETE FROM chat_intent_node
WHERE intent_code IN (SELECT intent_code FROM target);

DELETE FROM setting
WHERE setting_key LIKE 'code_search.%'
   OR category_code = 'code_search';

DELETE FROM mcp
WHERE mcp_code = 'code_search';
```

Edit `init.sql` to remove:
- `chat_intent_node` rows for `code` and `code-search`
- `setting` rows for `code_search.root`, `code_search.max_results`, `code_search.max_file_size_bytes`
- `mcp` row for `code_search`

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -Dtest=ChatIntentSeedScriptTest test`

Expected: PASS.

### Task 2: 后端执行器和配置清理

**Files:**
- Delete: `backend/src/main/java/com/codingx/mcp/application/executor/CodeSearchMcpToolExecutor.java`
- Delete: `backend/src/test/java/com/codingx/chat/application/service/CodeSearchMcpToolExecutorTest.java`
- Modify: `backend/src/main/java/com/codingx/config/RuntimeProperties.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/support/RuntimeSettingService.java`
- Modify: `backend/src/main/java/com/codingx/mcp/application/service/AdminChatMcpService.java`
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationMcpFlowTest.java`
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ConversationIntentServiceTest.java`
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatMcpQueryServiceTest.java`
- Modify: `backend/src/test/java/com/codingx/chat/interfaces/controller/AdminChatMcpControllerTest.java`
- Modify: `backend/src/test/java/com/codingx/chat/interfaces/controller/ChatStreamControllerTest.java`

- [ ] **Step 1: Write the failing test**

Run current targeted tests before production cleanup:

```bash
cd backend && mvn -Dtest=CodeSearchMcpToolExecutorTest,ChatApplicationMcpFlowTest,ConversationIntentServiceTest,ChatMcpQueryServiceTest,AdminChatMcpControllerTest test
```

Expected: PASS before cleanup, demonstrating tests still assert old code-search behavior and must be updated.

- [ ] **Step 2: Remove old code-search assertions**

Delete the code-search executor test file and remove `sendMessageExecutesCodeSearchToolForCodeIntent` from `ChatApplicationMcpFlowTest`.

Update remaining tests:
- `ConversationIntentServiceTest`: remove `routeReturnsMcpActionForCodeSearchIntent`.
- `ChatMcpQueryServiceTest`: use `weather_query` or another retained MCP instead of `code_search`.
- `AdminChatMcpControllerTest`: use retained tools only.
- `ChatStreamControllerTest`: remove `code_search` sample MCP entries.

- [ ] **Step 3: Remove production code**

Delete `CodeSearchMcpToolExecutor.java`.

Remove these fields from `RuntimeProperties`:

```java
private String codeSearchRoot = "";
private int codeSearchMaxResults = 20;
private long codeSearchMaxFileSizeBytes = 1024 * 1024L;
```

Remove these methods from `RuntimeSettingService`:

```java
public String codeSearchRoot()
public int codeSearchMaxResults()
public long codeSearchMaxFileSizeBytes()
```

Remove `case "code_search"` from `AdminChatMcpService.sampleQuestionFor`.

- [ ] **Step 4: Run targeted backend tests**

Run:

```bash
cd backend && mvn -Dtest=ChatApplicationMcpFlowTest,ConversationIntentServiceTest,ChatMcpQueryServiceTest,AdminChatMcpControllerTest,ChatStreamControllerTest test
```

Expected: PASS.

### Task 3: 管理端测试和硬编码清理

**Files:**
- Modify: `frontend/admin/src/pages/Settings.tsx`
- Modify: `frontend/admin/tests/pages/MCP.test.tsx`
- Modify: `frontend/admin/tests/pages/Skills.test.tsx`

- [ ] **Step 1: Write the failing checks**

Run:

```bash
cd frontend/admin && npm run test:run -- tests/pages/MCP.test.tsx tests/pages/Skills.test.tsx
```

Expected: PASS before cleanup, confirming current tests still use old mock data.

- [ ] **Step 2: Update frontend data and labels**

Remove `code_search: '代码检索运行时'` from `providerTitle`.

Replace `code_search` mock MCP rows with `weather_query` or other retained MCP rows in `MCP.test.tsx`.

Replace `code_search` mock skill rows in `Skills.test.tsx` with an existing retained skill code, such as `web-access`, if the test only needs a generic row.

- [ ] **Step 3: Run targeted admin tests**

Run:

```bash
cd frontend/admin && npm run test:run -- tests/pages/MCP.test.tsx tests/pages/Skills.test.tsx
```

Expected: PASS.

### Task 4: Documentation and feature docs

**Files:**
- Modify: `docs/features/index.md`
- Create or Modify: `docs/features/chat/mcp-runtime.md`

- [ ] **Step 1: Update feature docs**

Document that retained built-in MCP examples include weather and external MCP runtime, and that the legacy `code_search` built-in MCP has been removed by migration `V20260607_222030__remove_code_search_mcp_and_intents.sql`.

- [ ] **Step 2: Verify docs references**

Run:

```bash
rg -n "code_search|code-search|代码检索|代码查找" docs/features backend/src/main/resources/db/init.sql backend/src/main/java frontend/admin/src frontend/admin/tests
```

Expected: no production or feature-doc references to active code-search capability remain. Historical superpowers specs and migration history may still contain old references.

### Task 5: Full verification and browser evidence

**Files:**
- No direct file edits.

- [ ] **Step 1: Backend verification**

Run:

```bash
cd backend && mvn compile
cd backend && mvn test
```

Expected: PASS.

- [ ] **Step 2: Admin frontend verification**

Run:

```bash
cd frontend/admin && npm run build
cd frontend/admin && npm run test:run
```

Expected: PASS.

- [ ] **Step 3: Browser verification**

Use CDP through web-access against `http://localhost:5003/intent-tree`.

Expected:
- 意图树页面不包含“代码检索与定位”“代码查找”“code-search”。
- MCP 管理页不包含 `code_search`。
- Save screenshot evidence under `logs/`.

### Task 6: Review and commit

**Files:**
- All files changed by Tasks 1-4.

- [ ] **Step 1: Code review self-check**

Review diff for:
- unrelated dirty worktree files are untouched
- no active `code_search` production reference remains
- comments in modified Java files still explain business intent and boundary conditions

- [ ] **Step 2: Stage only this task's files**

Run:

```bash
git add backend/src/main/resources/db/migration/V20260607_222030__remove_code_search_mcp_and_intents.sql \
  backend/src/main/resources/db/init.sql \
  backend/src/main/java/com/codingx/mcp/application/executor/CodeSearchMcpToolExecutor.java \
  backend/src/main/java/com/codingx/config/RuntimeProperties.java \
  backend/src/main/java/com/codingx/chat/application/service/support/RuntimeSettingService.java \
  backend/src/main/java/com/codingx/mcp/application/service/AdminChatMcpService.java \
  backend/src/test/java/com/codingx/chat/application/service/CodeSearchMcpToolExecutorTest.java \
  backend/src/test/java/com/codingx/chat/application/service/ChatApplicationMcpFlowTest.java \
  backend/src/test/java/com/codingx/chat/application/service/ConversationIntentServiceTest.java \
  backend/src/test/java/com/codingx/chat/application/service/ChatMcpQueryServiceTest.java \
  backend/src/test/java/com/codingx/chat/interfaces/controller/AdminChatMcpControllerTest.java \
  backend/src/test/java/com/codingx/chat/interfaces/controller/ChatStreamControllerTest.java \
  backend/src/test/java/com/codingx/chat/infrastructure/persistence/repository/ChatIntentSeedScriptTest.java \
  frontend/admin/src/pages/Settings.tsx \
  frontend/admin/tests/pages/MCP.test.tsx \
  frontend/admin/tests/pages/Skills.test.tsx \
  docs/features/index.md docs/features/chat/mcp-runtime.md \
  docs/superpowers/specs/2026-06-07-222030-remove-code-search-mcp-design.md \
  docs/superpowers/acceptance/2026-06-07-222030-remove-code-search-mcp.md \
  docs/superpowers/plans/2026-06-07-222030-remove-code-search-mcp.md
```

- [ ] **Step 3: Commit**

Run:

```bash
git commit -m "fix(chat): 下线代码检索意图与工具"
```

Expected: commit succeeds with only this task's files.
