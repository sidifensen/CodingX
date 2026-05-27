# Chat Split Intent Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make mixed split questions route each sub-question independently so weather uses MCP instead of inherited web search.

**Architecture:** Add a local sub-question decision model inside `ChatApplicationService`, resolve decisions from `ConversationRewriteResult`, then execute MCP and search groups separately before the final AI synthesis. Preserve existing SSE events and run outcome persistence.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Mockito, Maven.

---

### Task 1: Mixed Split Routing Regression

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationSearchFlowTest.java`

- [x] **Step 1: Write the failing test**

Add a test that returns a split rewrite with three subquestions, stubs per-subquestion routing, and verifies `weather_query` is called for the weather subquestion while web search skips that weather text.

- [x] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -Dtest=ChatApplicationSearchFlowTest#sendMessageRoutesEachSplitQuestionBeforeExecutingSearchAndMcp test`

Expected: FAIL because current production code routes the combined rewritten question once and never calls `weather_query`.

### Task 2: Per-Subquestion Execution

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`

- [x] **Step 1: Resolve decisions per subquestion**

Create private helpers that normalize split questions and route each one independently. Add comments explaining the business rule: split first, then route, matching `ragent`.

- [x] **Step 2: Extract MCP execution helper**

Move the existing MCP execution block into a helper that receives the subquestion and intent decision, preserves `mcp-call` payloads, and appends tool evidence to history.

- [x] **Step 3: Execute grouped actions**

Use the subquestion decisions to collect search questions and run MCP tools before model synthesis. Keep short-circuit behavior for clarify and disabled MCP.

- [x] **Step 4: Run focused tests**

Run: `cd backend && mvn -Dtest=ChatApplicationSearchFlowTest,ChatApplicationMcpFlowTest test`

Expected: PASS.

### Task 3: Feature Documentation and Verification

**Files:**
- Modify: `docs/features/index.md`
- Create: `docs/features/chat/split-intent-routing.md`

- [x] **Step 1: Document current behavior**

Record the split-routing entry point, core flow, key files, and verification commands.

- [x] **Step 2: Run backend verification**

Run: `cd backend && mvn compile`

Expected: exit code 0.

Run: `cd backend && mvn test`

Expected: exit code 0. Actual: compile passed; full test suite failed in unrelated untracked admin test `AdminChatSettingsServiceTest.listAllSettingsRefreshesRuntimeCacheBeforeReturningList`, which expects `runtimeSettingService.refresh()` but the admin settings service only called `listAll()`.

- [ ] **Step 3: Commit**

Commit message: `fix(chat): 按子问题路由混合搜索与MCP调用`
