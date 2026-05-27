# Web Search Authoritative Freshness Ranking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复“最新/当前/版本”类搜索回答把过期权威结果排在更新权威结果前的问题。

**Architecture:** 在现有 `SearchChannel -> WebSearchExecutionService -> SearchResultPostProcessor` 链路中新增通用权威时效排序后处理，不改 provider 协议。查询改写层不追加特定厂商或域名锚点，证据上下文明确权威来源、版本号、日期和未确认来源的冲突规则。

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
// 权威文档源的 2.5 版本应排在同源 2.4 版本之前，即使原始搜索分数更低。
```

```java
// ConversationRewriteServiceTest
// 最新版本类问题在查询归一化后不应补充任何特定厂商或域名限定词。
```

- [ ] **Step 2: Verify RED**

Run:

```bash
cd backend
mvn -Dtest=WebSearchExecutionServiceTest,ConversationRewriteServiceTest test
```

Expected: new ranking assertions fail because the old processor does not understand generic version signals.

### Task 2: Implementation

**Files:**
- Create: `backend/src/main/java/com/codingx/chat/application/service/search/AuthoritativeLatestSearchPostProcessor.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationRewriteService.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`
- Modify: `docs/features/chat/search.md`
- Modify: `docs/features/index.md`

- [ ] **Step 1: Implement search post processor**

Create a focused `SearchResultPostProcessor` that only activates for freshness questions. It should:

```java
// 激活条件：问题包含最新/当前/版本/发布/latest/current/version/release。
// 排序信号：通用文档/开发者/支持站点形态优先，标题/URL/摘要中的语义版本号和日期越新越优。
```

- [ ] **Step 2: Remove vendor-specific query hint**

Keep `ConversationRewriteService` limited to term normalization and prompt-driven rewrite. Do not append hardcoded vendor, product, or domain hints.

- [ ] **Step 3: Tighten evidence instructions**

Add evidence constraints that latest/current public product questions must prefer official product or developer docs, compare version-like identifiers and dates, and state uncertainty when only non-official evidence supports a newer claim.

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
fix(search): 泛化联网搜索权威时效排序
```
