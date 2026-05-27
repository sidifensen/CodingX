# Web Search Authoritative Latest Ranking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复“最新 GPT 模型”类搜索回答把过期官方结果排在更新官方结果前的问题。

**Architecture:** 在现有 `SearchChannel -> WebSearchExecutionService -> SearchResultPostProcessor` 链路中新增权威最新排序后处理，不改 provider 协议。查询改写层对 OpenAI/GPT 最新模型问题追加官方来源提示，证据上下文明确官方来源和版本号冲突规则。

**Tech Stack:** Java 21, Spring Boot, Hutool, JUnit 5, Mockito.

---

### Task 1: Regression Tests

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/WebSearchExecutionServiceTest.java`
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ConversationRewriteServiceTest.java`

- [ ] **Step 1: Write failing tests**

Add tests that assert:

```java
// WebSearchExecutionServiceTest
// 官方 OpenAI GPT 5.5 结果应排在 GPT 5.4 结果之前，即使原始搜索分数更低。
```

```java
// ConversationRewriteServiceTest
// “gpt 最新模型”应在查询归一化后补充 OpenAI 官方文档限定词。
```

- [ ] **Step 2: Verify RED**

Run:

```bash
cd backend
mvn -Dtest=WebSearchExecutionServiceTest,ConversationRewriteServiceTest test
```

Expected: new assertions fail because no authoritative latest ranking and no GPT latest query hint exist yet.

### Task 2: Implementation

**Files:**
- Create: `backend/src/main/java/com/codingx/chat/application/service/search/AuthoritativeLatestSearchPostProcessor.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationRewriteService.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`
- Modify: `docs/features/chat/search.md`
- Modify: `docs/features/index.md`

- [ ] **Step 1: Implement search post processor**

Create a focused `SearchResultPostProcessor` that only activates for latest/current model questions. It should:

```java
// 激活条件：问题包含最新/当前/latest/current 且包含 gpt/openai/模型/model。
// 排序信号：官方域名优先、OpenAI 官方 docs 域名更优、标题/URL/摘要中的 GPT 版本号越新越优。
```

- [ ] **Step 2: Add GPT latest query hint**

Extend `ConversationRewriteService` so a no-history question containing GPT/OpenAI latest model intent appends `OpenAI 官方文档 latest model developers.openai.com` when missing. Keep it narrow to avoid polluting unrelated searches.

- [ ] **Step 3: Tighten evidence instructions**

Add evidence constraints that latest/current public product questions must prefer official product or developer docs, compare version-like identifiers, and state uncertainty when only non-official evidence supports a newer claim.

- [ ] **Step 4: Verify GREEN**

Run:

```bash
cd backend
mvn -Dtest=WebSearchExecutionServiceTest,ConversationRewriteServiceTest test
```

Expected: targeted tests pass.

### Task 3: Full Backend Verification And Commit

**Files:**
- No additional source changes unless verification exposes a defect in this task scope.

- [ ] **Step 1: Run changed-surface verification**

Run:

```bash
cd backend
mvn compile
mvn test
```

- [ ] **Step 2: Commit only this task's files**

Stage only search fix files and docs. Commit message:

```bash
fix(search): 修复最新模型搜索证据排序
```
