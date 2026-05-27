# 聊天歧义引导配置化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `ragent` 的歧义引导配置和候选判定流程迁移到 CodingX，并通过系统配置数据库开放中文可配置参数。

**Architecture:** 保持 `ConversationIntentService` 的既有入口不变，把 `ConversationIntentGuidanceService` 收敛为编排层。新增路径解析器和歧义检测器承载 `ragent` 的系统去重、显式系统名跳过、阈值直判、边界 LLM 复核和候选裁剪逻辑。

**Tech Stack:** Spring Boot、JUnit 5、Mockito、Hutool、PostgreSQL SQL migration、React/Vitest。

---

## File Structure

- Modify: `backend/src/main/java/com/codingx/chat/application/service/support/RuntimeSettingService.java`
  - Add `DECIMAL` parsing and `chat.intent.guidance.*` typed accessors.
- Modify: `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationIntentGuidanceService.java`
  - Keep trace annotation and prompt rendering, delegate ambiguity detection.
- Create: `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationIntentPathResolver.java`
  - Build node index, full paths, system names, system identity, and normalized matching.
- Create: `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationIntentAmbiguityDetector.java`
  - Port the `ragent` decision flow using `RuntimeSettingService`, `PromptTemplateLoader`, and `AiPromptExecutionService`.
- Modify: `backend/src/test/java/com/codingx/chat/application/service/support/RuntimeSettingServiceTest.java`
  - Cover default and configured guidance settings.
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ConversationIntentGuidanceServiceTest.java`
  - Cover switch, threshold, LLM boundary, explicit system skip, system dedupe, max options.
- Create: `backend/src/main/resources/db/migration/V20260527_200000__add_chat_intent_guidance_settings.sql`
  - Insert four system settings with Chinese descriptions.
- Modify: `backend/src/main/resources/db/init.sql`
  - Add the same four settings to baseline init data.
- Modify: `backend/src/test/java/com/codingx/chat/infrastructure/persistence/repository/ChatRuntimePersistenceStructureTest.java`
  - Assert migration and init SQL contain the guidance setting keys and Chinese descriptions.
- Modify: `frontend/admin/src/pages/Settings.tsx`
  - Add Chinese category label `chat.intent.guidance -> 歧义引导`.
- Modify: `frontend/admin/tests/pages/Settings.test.tsx`
  - Assert the new category label renders when API returns guidance settings.
- Create: `docs/features/chat/intent-guidance-config.md`
  - Document current behavior, configuration keys, files, and verification.
- Modify: `docs/features/index.md`
  - Add the feature document link under Chat.

### Task 1: Runtime Setting Accessors

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/support/RuntimeSettingServiceTest.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/support/RuntimeSettingService.java`

- [ ] **Step 1: Write failing tests for guidance settings**

Add tests asserting configured and default values:

```java
@Test
void chatIntentGuidanceReadsConfiguredValues() {
    when(chatRuntimeSettingRepository.findAll()).thenReturn(List.of(
        ChatRuntimeSetting.builder().settingKey("chat.intent.guidance.enabled").settingValue("false").valueType("BOOLEAN").build(),
        ChatRuntimeSetting.builder().settingKey("chat.intent.guidance.ambiguity_score_ratio").settingValue("0.72").valueType("DECIMAL").build(),
        ChatRuntimeSetting.builder().settingKey("chat.intent.guidance.ambiguity_margin").settingValue("0.08").valueType("DECIMAL").build(),
        ChatRuntimeSetting.builder().settingKey("chat.intent.guidance.max_options").settingValue("3").valueType("INTEGER").build()
    ));

    runtimeSettingService.init();

    assertEquals(false, runtimeSettingService.chatIntentGuidanceEnabled());
    assertEquals(0.72D, runtimeSettingService.chatIntentGuidanceAmbiguityScoreRatio());
    assertEquals(0.08D, runtimeSettingService.chatIntentGuidanceAmbiguityMargin());
    assertEquals(3, runtimeSettingService.chatIntentGuidanceMaxOptions());
}
```

- [ ] **Step 2: Run targeted failing test**

Run: `cd backend; mvn "-Dtest=RuntimeSettingServiceTest#chatIntentGuidanceReadsConfiguredValues" test`

Expected: compilation fails because the new accessor methods do not exist.

- [ ] **Step 3: Implement decimal parsing and accessors**

Add `TYPE_DECIMAL`, `getDouble(String key, double fallback)`, and these methods:

```java
public boolean chatIntentGuidanceEnabled() {
    return getBoolean("chat.intent.guidance.enabled", true);
}

public double chatIntentGuidanceAmbiguityScoreRatio() {
    return getDouble("chat.intent.guidance.ambiguity_score_ratio", 0.8D);
}

public double chatIntentGuidanceAmbiguityMargin() {
    return getDouble("chat.intent.guidance.ambiguity_margin", 0.15D);
}

public int chatIntentGuidanceMaxOptions() {
    return getInt("chat.intent.guidance.max_options", 6);
}
```

- [ ] **Step 4: Run runtime setting tests**

Run: `cd backend; mvn "-Dtest=RuntimeSettingServiceTest" test`

Expected: `RuntimeSettingServiceTest` passes.

### Task 2: Split Ambiguity Detection

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ConversationIntentGuidanceServiceTest.java`
- Create: `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationIntentPathResolver.java`
- Create: `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationIntentAmbiguityDetector.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationIntentGuidanceService.java`

- [ ] **Step 1: Write failing guidance tests**

Update test construction to inject `RuntimeSettingService` mock and assert these named scenarios:

```java
when(runtimeSettingService.chatIntentGuidanceEnabled()).thenReturn(true);
when(runtimeSettingService.chatIntentGuidanceAmbiguityScoreRatio()).thenReturn(0.8D);
when(runtimeSettingService.chatIntentGuidanceAmbiguityMargin()).thenReturn(0.15D);
when(runtimeSettingService.chatIntentGuidanceMaxOptions()).thenReturn(6);
```

Add tests for disabled switch, boundary LLM true/false, explicit system name skip, and `maxOptions=2` trimming.

- [ ] **Step 2: Run targeted failing tests**

Run: `cd backend; mvn "-Dtest=ConversationIntentGuidanceServiceTest" test`

Expected: compilation fails because the new collaborator constructors and behavior are not implemented.

- [ ] **Step 3: Create `ConversationIntentPathResolver`**

Implement methods:

```java
Map<String, ChatIntentNode> indexByCode(List<ChatIntentNode> nodes)
String resolveFullPath(ChatIntentNode node, Map<String, ChatIntentNode> nodeByCode)
String resolveSystemName(ChatIntentNode node, Map<String, ChatIntentNode> nodeByCode)
String resolveSystemIdentity(ChatIntentNode node, Map<String, ChatIntentNode> nodeByCode)
boolean questionContainsSystemName(String question, List<ConversationIntentCandidate> ranked, Map<String, ChatIntentNode> nodeByCode)
String normalize(String value)
```

Use Hutool `StrUtil` for blank checks and retain comments describing parent-chain boundary decisions.

- [ ] **Step 4: Create `ConversationIntentAmbiguityDetector`**

Implement `detect(String question, List<ConversationIntentCandidate> candidates, List<ChatIntentNode> allNodes)` returning an internal record with topic, ranked candidates, and node index. Mirror `ragent` order: enabled check, valid candidate filtering, system de-duplication, explicit system skip, threshold direct decision, boundary LLM check, max option trimming.

- [ ] **Step 5: Refactor `ConversationIntentGuidanceService`**

Inject `ConversationIntentAmbiguityDetector` and `ConversationIntentPathResolver`; keep only `buildGuidancePrompt(...)` and option rendering. The output format remains:

```text
1) 完整路径
2) 完整路径
```

- [ ] **Step 6: Run guidance tests**

Run: `cd backend; mvn "-Dtest=ConversationIntentGuidanceServiceTest" test`

Expected: all guidance tests pass.

### Task 3: Database Configuration Seeds

**Files:**
- Create: `backend/src/main/resources/db/migration/V20260527_200000__add_chat_intent_guidance_settings.sql`
- Modify: `backend/src/main/resources/db/init.sql`
- Modify: `backend/src/test/java/com/codingx/chat/infrastructure/persistence/repository/ChatRuntimePersistenceStructureTest.java`

- [ ] **Step 1: Write failing SQL structure assertions**

Add checks for:

```java
"chat.intent.guidance.enabled"
"chat.intent.guidance.ambiguity_score_ratio"
"chat.intent.guidance.ambiguity_margin"
"chat.intent.guidance.max_options"
"是否启用聊天歧义引导"
```

- [ ] **Step 2: Run targeted failing test**

Run: `cd backend; mvn "-Dtest=ChatRuntimePersistenceStructureTest" test`

Expected: test fails because the new migration file and SQL entries are missing.

- [ ] **Step 3: Add migration script**

Insert four rows into `setting` with IDs `7069` through `7072`, category `chat.intent.guidance`, Chinese descriptions, and `ON CONFLICT (setting_key) DO UPDATE`.

- [ ] **Step 4: Update `db/init.sql`**

Add the same four rows to the main `INSERT INTO setting` values list. Keep the existing unrelated tool description changes intact and stage only the new hunk later.

- [ ] **Step 5: Run SQL structure test**

Run: `cd backend; mvn "-Dtest=ChatRuntimePersistenceStructureTest" test`

Expected: test passes.

### Task 4: Admin Settings Category Label

**Files:**
- Modify: `frontend/admin/src/pages/Settings.tsx`
- Modify: `frontend/admin/tests/pages/Settings.test.tsx`

- [ ] **Step 1: Write failing UI unit test**

Extend the mocked settings list with a row:

```ts
{
  settingKey: 'chat.intent.guidance.enabled',
  settingValue: 'true',
  valueType: 'BOOLEAN',
  categoryCode: 'chat.intent.guidance',
  description: '是否启用聊天歧义引导',
  sortNo: 10,
  restartRequired: false,
}
```

Assert `await screen.findByText('歧义引导')`.

- [ ] **Step 2: Run failing frontend test**

Run: `cd frontend/admin; npm run test:run -- Settings.test.tsx`

Expected: test fails because `CATEGORY_LABELS` lacks the new category.

- [ ] **Step 3: Add category label**

Add:

```ts
'chat.intent.guidance': '歧义引导',
```

Keep existing theme-aware classes unchanged.

- [ ] **Step 4: Run frontend test**

Run: `cd frontend/admin; npm run test:run -- Settings.test.tsx`

Expected: `Settings.test.tsx` passes.

### Task 5: Feature Documentation

**Files:**
- Create: `docs/features/chat/intent-guidance-config.md`
- Modify: `docs/features/index.md`

- [ ] **Step 1: Add feature doc**

Document purpose, entry, core flow, configuration keys, key files, and verification commands. State that settings are stored in `setting` and descriptions are Chinese.

- [ ] **Step 2: Update feature index**

Add `- [聊天歧义引导配置](chat/intent-guidance-config.md)` under Chat. Keep unrelated index edits intact.

### Task 6: Full Verification And Commit

**Files:**
- All files touched above.

- [ ] **Step 1: Run backend verification**

Run:

```powershell
cd backend
mvn compile
mvn test
```

Expected: both commands exit with code 0.

- [ ] **Step 2: Run admin frontend verification**

Run:

```powershell
cd frontend/admin
npm run build
npm run test:run
```

Expected: both commands exit with code 0.

- [ ] **Step 3: Review staged boundaries**

Run:

```powershell
git diff --name-only
git diff --cached --name-only
```

Expected: only this task's files are staged for the final commit; unrelated pre-existing staged or dirty files remain untouched.

- [ ] **Step 4: Commit implementation**

Stage only this task's paths, using partial staging for files that already had unrelated changes:

```powershell
git add backend/src/main/java/com/codingx/chat/application/service/support/RuntimeSettingService.java
git add backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationIntentGuidanceService.java
git add backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationIntentPathResolver.java
git add backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationIntentAmbiguityDetector.java
git add backend/src/test/java/com/codingx/chat/application/service/support/RuntimeSettingServiceTest.java
git add backend/src/test/java/com/codingx/chat/application/service/ConversationIntentGuidanceServiceTest.java
git add backend/src/main/resources/db/migration/V20260527_200000__add_chat_intent_guidance_settings.sql
git add frontend/admin/src/pages/Settings.tsx
git add frontend/admin/tests/pages/Settings.test.tsx
git add docs/features/chat/intent-guidance-config.md
git add -p backend/src/main/resources/db/init.sql
git add -p backend/src/test/java/com/codingx/chat/infrastructure/persistence/repository/ChatRuntimePersistenceStructureTest.java
git add -p docs/features/index.md
git commit -m "feat(chat): 接入歧义引导运行时配置"
```

Expected: commit contains only current-task changes.
