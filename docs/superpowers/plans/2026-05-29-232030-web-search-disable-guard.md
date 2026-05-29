# Web Search Disable Guard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Disable the automatic built-in web search path when the system search switch is off or `web-access` is explicitly selected.

**Architecture:** Add a guard after intent routing and before search question extraction in `ChatApplicationService`. Search decisions are downgraded to direct model handling when the runtime switch or selected skill requires suppression.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito.

---

### Task 1: Guard Automatic Search Execution

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationSearchFlowTest.java`
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationServiceTest.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`

- [x] **Step 1: Write failing regression tests**

Add tests covering `web_search.enabled=false` and explicit `web-access` skill selection. Both tests assert that the system search service, execution step persistence, search reference collector, and document artifact service are not called.

- [x] **Step 2: Verify red state**

Run: `mvn -Dtest=ChatApplicationSearchFlowTest#sendMessageSkipsSearchFlowWhenWebSearchDisabled+sendMessageWithWebAccessSkillSkipsSystemSearchFlow test`

Observed: Maven testCompile is blocked by unrelated existing `AdminChatSkillServiceTest` and `AdminChatSkillControllerTest` calls to an old `uploadSkillPackage` method signature, so the new tests cannot execute in this workspace until those unrelated tests are corrected.

- [x] **Step 3: Implement search suppression**

Add `suppressAutomaticSearchDecisions(...)` to convert `SEARCH` decisions to `DIRECT` when `RuntimeSettingService.webSearchEnabled()` is false or `skillCodes` contains `web-access`.

- [x] **Step 4: Preserve existing enabled-search tests**

Update existing search-flow tests to explicitly stub `runtimeSettingService.webSearchEnabled()` as `true`, making the intended enabled-search behavior clear.

- [x] **Step 5: Verify production compile**

Run: `mvn -DskipTests compile`

Expected: build success.
