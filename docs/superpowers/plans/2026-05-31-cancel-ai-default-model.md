# Cancel AI Default Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove AI default model pointers from runtime routing, database defaults, admin settings, and feature docs.

**Architecture:** `AiModelSelector` keeps explicit `preferredModel` support but falls back to candidate `priority` only when no explicit preference exists. Database initialization omits default model settings, and a forward migration marks historical keys deleted. Admin settings no longer special-cases those keys.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, PostgreSQL SQL migrations, React 19, Ant Design, Vitest.

---

### Task 1: Backend Selector Behavior

**Files:**
- Modify: `backend/src/test/java/com/codingx/support/ai/AiModelSelectorTest.java`
- Modify: `backend/src/main/java/com/codingx/common/support/ai/AiModelSelector.java`
- Modify: `backend/src/main/java/com/codingx/config/DynamicAiProperties.java`
- Modify: `backend/src/test/java/com/codingx/config/DynamicAiPropertiesTest.java`

- [ ] **Step 1: Write failing selector tests**

Replace tests that expect default model pointers with tests expecting priority-only fallback:

```java
@Test
void selectChatCandidatesUsesPriorityWhenRequestHasNoPreference() {
    AiProperties properties = buildProperties(
        candidate("priority-first", "deepseek", "deepseek-chat", 1, false),
        candidate("historical-default", "stub", "stub-chat", 9, false)
    );
    properties.getChat().setDefaultModel("historical-default");
    AiModelSelector selector = new AiModelSelector(properties);

    List<AiModelTarget> targets = selector.selectChatCandidates(null, false);

    assertEquals("priority-first", targets.getFirst().id());
}
```

- [ ] **Step 2: Run RED backend selector tests**

Run: `cd backend && mvn -Dtest=AiModelSelectorTest,DynamicAiPropertiesTest test`

Expected: FAIL because selector still applies `defaultModel` / `deepThinkingModel`, and dynamic default methods still exist in tests.

- [ ] **Step 3: Remove default pointer routing**

Update `AiModelSelector` so `chatGroupWithFallback()` no longer calls `dynamicAiProperties.defaultChatModel()` or `deepThinkingChatModel()`, and `resolveFirstChoiceModel(...)` returns only nonblank `preferredModel`.

- [ ] **Step 4: Remove dynamic default methods and tests**

Delete `DynamicAiProperties.defaultChatModel()` and `DynamicAiProperties.deepThinkingChatModel()`. Remove their unit tests from `DynamicAiPropertiesTest`.

- [ ] **Step 5: Run GREEN backend selector tests**

Run: `cd backend && mvn -Dtest=AiModelSelectorTest,DynamicAiPropertiesTest test`

Expected: PASS.

### Task 2: Database And Config Tests

**Files:**
- Modify: `backend/src/test/java/com/codingx/config/AiRoutingDefaultsConfigTest.java`
- Modify: `backend/src/main/resources/db/init.sql`
- Create: `backend/src/main/resources/db/migration/V20260531_222700__remove_ai_default_model_settings.sql`

- [ ] **Step 1: Write failing SQL tests**

Update `initSqlSeedsSiliconFlowRuntimeDefaults` to assert default model keys are absent and rename the test/comment to reflect candidate pool defaults. Add a test requiring a cleanup migration that contains both keys and `deleted = 1`.

- [ ] **Step 2: Run RED SQL tests**

Run: `cd backend && mvn -Dtest=AiRoutingDefaultsConfigTest test`

Expected: FAIL because `init.sql` still contains both keys and no cleanup migration exists.

- [ ] **Step 3: Update SQL baseline and migration**

Remove rows `7053` and `7054` from `init.sql`. Add migration:

```sql
-- 默认模型指针已退出路由，历史配置标记删除后由候选池 priority 决定默认顺序。
UPDATE setting
SET deleted = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE setting_key IN ('ai.chat.default_model', 'ai.chat.deep_thinking_model');
```

- [ ] **Step 4: Run GREEN SQL tests**

Run: `cd backend && mvn -Dtest=AiRoutingDefaultsConfigTest test`

Expected: PASS.

### Task 3: Admin Settings UI

**Files:**
- Modify: `frontend/admin/src/pages/Settings.tsx`
- Modify: `frontend/admin/tests/pages/Settings.test.tsx`

- [ ] **Step 1: Write failing UI tests**

Remove default model entries from `mockSettings`. Replace the default model test with an assertion that AI 基础配置 does not display default model labels or test IDs.

- [ ] **Step 2: Run RED UI tests**

Run: `cd frontend/admin && npm run test:run -- Settings.test.tsx`

Expected: FAIL because the component still uses `AI_BASE_SETTING_KEYS` and the old test data path may still surface special handling.

- [ ] **Step 3: Remove UI special casing**

Delete `AI_BASE_SETTING_KEYS` and make `displayCategoryCode` return `setting.categoryCode ?? 'general'` directly. Keep existing category labels and theme styles unchanged.

- [ ] **Step 4: Run GREEN UI tests**

Run: `cd frontend/admin && npm run test:run -- Settings.test.tsx`

Expected: PASS.

### Task 4: Feature Docs And Final Verification

**Files:**
- Modify: `docs/features/chat/ai-model-failover.md`
- Modify: `docs/features/chat/ai-routing-defaults.md`
- Modify: `docs/superpowers/memory/chat/ai-routing-selection-contract.md`

- [ ] **Step 1: Update docs**

Document that default routing order now comes from candidate `priority`, with explicit `preferredModel` as the only first-choice override.

- [ ] **Step 2: Run targeted verification**

Run:

```bash
cd backend && mvn -Dtest=AiModelSelectorTest,DynamicAiPropertiesTest,AiRoutingDefaultsConfigTest test
cd frontend/admin && npm run test:run -- Settings.test.tsx
```

Expected: PASS.

- [ ] **Step 3: Run full changed-surface verification**

Run:

```bash
cd backend && mvn compile
cd backend && mvn test
cd frontend/admin && npm run build
cd frontend/admin && npm run test:run
```

Expected: PASS unless unrelated dirty worktree changes cause failures; if so, stop and report the unrelated failure source.

- [ ] **Step 4: Commit**

Run:

```bash
git add backend/src/main/java/com/codingx/common/support/ai/AiModelSelector.java backend/src/main/java/com/codingx/config/DynamicAiProperties.java backend/src/test/java/com/codingx/support/ai/AiModelSelectorTest.java backend/src/test/java/com/codingx/config/DynamicAiPropertiesTest.java backend/src/test/java/com/codingx/config/AiRoutingDefaultsConfigTest.java backend/src/main/resources/db/init.sql backend/src/main/resources/db/migration/V20260531_222700__remove_ai_default_model_settings.sql frontend/admin/src/pages/Settings.tsx frontend/admin/tests/pages/Settings.test.tsx docs/features/chat/ai-model-failover.md docs/features/chat/ai-routing-defaults.md docs/superpowers/memory/chat/ai-routing-selection-contract.md docs/superpowers/specs/2026-05-31-222656-cancel-ai-default-model-design.md docs/superpowers/acceptance/2026-05-31-cancel-ai-default-model.md docs/superpowers/plans/2026-05-31-cancel-ai-default-model.md docs/superpowers/memory/chat/ai-routing-module-card.md docs/superpowers/memory/reports/2026-05-31-ai-routing-bootstrap-report.md docs/superpowers/memory/index.md
git commit -m "refactor(ai-routing): 取消默认模型设置"
```
