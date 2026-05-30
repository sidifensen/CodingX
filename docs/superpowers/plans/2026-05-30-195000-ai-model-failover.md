# AI 模型故障切换策略 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 ragent 的模型候选路由、首包探测与三态熔断策略完整适配到 CodingX 聊天模型调用链路。

**Architecture:** 保留 CodingX 现有 `AiChatClient` 领域接口和 OpenAI 兼容 provider 实现，在 `common.support.ai` 路由层补齐 ragent 策略。选择器负责候选排序与 provider 装配，调度服务负责首包探测、失败标记、取消与 fallback，健康注册表负责三态熔断。

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, Hutool.

---

### Task 1: Selector 首选模型语义

**Files:**
- Modify: `backend/src/test/java/com/codingx/support/ai/AiModelSelectorTest.java`
- Modify: `backend/src/main/java/com/codingx/common/support/ai/AiModelSelector.java`

- [ ] **Step 1: Write failing selector tests**

Add tests proving `defaultModel` and `deepThinkingModel` are used when no request-level preferred model exists.

- [ ] **Step 2: Run selector tests and verify RED**

Run: `cd backend && mvn -Dtest=AiModelSelectorTest test`

Expected: new tests fail because `resolveFirstChoiceModel` returns `null` without request preference.

- [ ] **Step 3: Implement selector priority**

Update `resolveFirstChoiceModel` so request preference wins, then thinking mode uses `group.deepThinkingModel`, otherwise `group.defaultModel`.

- [ ] **Step 4: Run selector tests and verify GREEN**

Run: `cd backend && mvn -Dtest=AiModelSelectorTest test`

Expected: all selector tests pass.

### Task 2: Dispatch 请求状态隔离与缺失客户端 fallback

**Files:**
- Modify: `backend/src/test/java/com/codingx/support/ai/AiModelDispatchServiceTest.java`
- Modify: `backend/src/main/java/com/codingx/common/support/ai/AiModelDispatchService.java`

- [ ] **Step 1: Write failing dispatch tests**

Add tests for missing provider client skip and concurrent request attempt recording isolation.

- [ ] **Step 2: Run dispatch tests and verify RED**

Run: `cd backend && mvn -Dtest=AiModelDispatchServiceTest test`

Expected: concurrent attempt state can be polluted because `lastAttemptedProviders` is a shared mutable list.

- [ ] **Step 3: Implement isolated attempt context**

Use a method-local attempted provider list during each dispatch and publish an immutable snapshot to a volatile field after each request completes.

- [ ] **Step 4: Run dispatch tests and verify GREEN**

Run: `cd backend && mvn -Dtest=AiModelDispatchServiceTest test`

Expected: all dispatch tests pass.

### Task 3: Docs and Full Verification

**Files:**
- Create: `docs/features/chat/ai-model-failover.md`
- Modify: `docs/features/index.md`

- [ ] **Step 1: Document current implementation**

Record purpose, entry points, core flow, key files, and verification commands.

- [ ] **Step 2: Run backend verification**

Run: `cd backend && mvn compile && mvn test`

Expected: build and tests pass, unless unrelated existing failures are identified and reported.
