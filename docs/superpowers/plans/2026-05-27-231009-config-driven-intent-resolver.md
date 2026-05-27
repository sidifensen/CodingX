# Config Driven Intent Resolver Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace brittle keyword-first intent routing with a ragent-style, configuration-driven classifier path.

**Architecture:** Keep the existing CodingX `ConversationIntentResolver` public API, but align internals with ragent: leaf-node candidates are described by full path, description, type and examples; the model returns JSON scores; fallback scoring uses configured node text only. Search/weather business semantics must live in intent node data, not Java keyword constants.

**Tech Stack:** Java 21, Spring Boot, Hutool JSON, JUnit 5, Mockito.

---

### Task 1: Rewrite Resolver Tests

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ConversationIntentResolverTest.java`

- [x] **Step 1: Replace heuristic-first tests with model/config-driven tests**

Update explicit search and weather tests so they mock `promptTemplateLoader.render(...)` and `aiPromptExecutionService.complete(...)`. The tests should assert:

```java
when(promptTemplateLoader.render(anyString(), org.mockito.ArgumentMatchers.anyMap())).thenReturn("intent prompt");
when(aiPromptExecutionService.complete("intent prompt", "请联网搜索最新 Java 版本")).thenReturn("""
    [{"id":"search-general","score":0.96,"reason":"问题明确要求联网检索最新版本信息"}]
    """);
```

For weather:

```java
when(promptTemplateLoader.render(anyString(), org.mockito.ArgumentMatchers.anyMap())).thenReturn("intent prompt");
when(aiPromptExecutionService.complete("intent prompt", "广州最近天气怎么样")).thenReturn("""
    [{"id":"weather-data","score":0.97,"reason":"问题询问城市天气，应调用天气 MCP"}]
    """);
```

- [x] **Step 2: Add a fallback test**

Add a test where the model throws and configured weather description/examples still produce `weather-data` via generic fallback:

```java
when(aiPromptExecutionService.complete("intent prompt", "广州最近天气怎么样")).thenThrow(new IllegalStateException("llm unavailable"));
```

Expected: top candidate code is `weather-data`.

- [x] **Step 3: Run RED**

Run:

```bash
cd backend
mvn "-Dtest=ConversationIntentResolverTest" test
```

Expected: tests fail before production code changes because existing resolver short-circuits heuristics or fallback cannot use examples/description robustly enough.

### Task 2: Refactor Resolver

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationIntentResolver.java`

- [x] **Step 1: Remove hardcoded business keyword lists**

Delete search/weather/news/fact keyword constants and helpers that classify by those hardcoded terms:

```java
SEARCH_INTENT_KEYWORDS
SEARCH_NEWS_KEYWORDS
SEARCH_FACT_KEYWORDS
WEATHER_QUERY_KEYWORDS
heuristicCandidates(...)
resolveSearchHeuristicScore(...)
resolveMcpOrSystemHeuristicScore(...)
isWeatherMcpNode(...)
isGeneralSearchNode(...)
isNewsSearchNode(...)
isFactsSearchNode(...)
containsAnyKeyword(...)
```

- [x] **Step 2: Make model classification primary**

In `resolveCandidates(...)`, build the prompt and call `aiPromptExecutionService.complete(...)` before any local fallback. Return parsed candidates when non-empty.

- [x] **Step 3: Add generic configured-text fallback**

Replace fallback with data-driven scoring that only reads configured `name`, full path, `description`, `examples`, and optional `mcpToolId`. Exact/contains example match should score highest; token overlap across configured text should provide lower fallback scores. No weather/search-specific terms may appear in Java code.

- [x] **Step 4: Run GREEN**

Run:

```bash
cd backend
mvn "-Dtest=ConversationIntentResolverTest" test
```

Expected: tests pass.

### Task 3: Update Feature Documentation

**Files:**
- Modify: `docs/features/chat/split-intent-routing.md`

- [x] **Step 1: Remove weather hardcoding wording**

Change the key logic section to say intent classification is based on the configured intent tree and examples. Weather MCP wins because its configured node description/examples match the weather subquestion, not because Java has weather keywords.

### Task 4: Final Verification And Commit

**Files:**
- Verify: `backend/src/test/java/com/codingx/chat/application/service/ConversationIntentResolverTest.java`
- Verify: `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationIntentResolver.java`
- Verify: `docs/features/chat/split-intent-routing.md`

- [x] **Step 1: Run focused verification**

Run:

```bash
cd backend
mvn "-Dtest=ConversationIntentResolverTest" test
```

- [ ] **Step 2: Commit only task files**

Use an alternate git index or explicit path staging so unrelated dirty files are not included.

Commit message:

```bash
git commit -m "fix(chat): 对齐配置驱动意图识别"
```
