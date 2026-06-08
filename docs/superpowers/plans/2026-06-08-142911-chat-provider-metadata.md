# Chat Provider Metadata Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复聊天消息落库时 `chat_message.provider` 记录协议适配器名的问题，改为记录候选池中的真实模型商 provider。

**Architecture:** `AiModelDispatchService` 继续用 `AiProviderClient.provider()` 解析具体客户端，但对外 metadata、尝试记录和首包错误上下文统一使用 `AiModelTarget.candidate().getProvider()`。这样 `openai-compatible` 仍是内部适配器编码，`bailian`、`siliconflow` 等真实 provider 会进入聊天消息审计字段。

**Tech Stack:** Java 21、Spring Boot、JUnit 5、Maven。

---

### Task 1: 锁定 OpenAI 兼容 provider 的 metadata 语义

**Files:**
- Modify: `backend/src/test/java/com/codingx/support/ai/AiModelDispatchServiceTest.java`

- [x] **Step 1: Write the failing test**

在 `AiModelDispatchServiceTest` 中新增测试：候选 provider 是 `bailian`，可用客户端只有 `openai-compatible` 时，`onMetadata` 和 `getLastAttemptedProviders()` 都应输出 `bailian`。

- [x] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -Dtest=AiModelDispatchServiceTest#streamChatReportsCandidateProviderWhenCompatibleClientHandlesVendor test`

Expected: FAIL，当前实现会输出 `openai-compatible:qwen-plus-latest`。

### Task 2: 修复路由层 metadata 输出

**Files:**
- Modify: `backend/src/main/java/com/codingx/common/support/ai/AiModelDispatchService.java`

- [x] **Step 1: Write minimal implementation**

在每个候选循环内定义 `logicalProvider = target.candidate().getProvider()`，客户端解析仍使用该值；成功 metadata、尝试列表和首包错误上下文改用 `logicalProvider`。

- [x] **Step 2: Run focused test**

Run: `cd backend && mvn -Dtest=AiModelDispatchServiceTest#streamChatReportsCandidateProviderWhenCompatibleClientHandlesVendor test`

Expected: PASS。

### Task 3: 更新文档并完成验证

**Files:**
- Modify: `backend/src/test/java/com/codingx/config/AiRoutingDefaultsConfigTest.java`
- Create: `backend/src/main/resources/db/migration/V20260608_144700__backfill_chat_message_provider_from_candidates.sql`
- Modify: `docs/features/chat/ai-model-failover.md`

- [x] **Step 1: Write migration existence test**

在 `AiRoutingDefaultsConfigTest` 中新增迁移存在性测试，要求历史 `openai-compatible` 消息必须能按唯一候选模型名回填真实 provider。

- [x] **Step 2: Run migration test to verify it fails**

Run: `cd backend && mvn -Dtest=AiRoutingDefaultsConfigTest#migrationBackfillsOpenAiCompatibleChatMessageProviderFromUniqueCandidateModel test`

Expected: FAIL，当前没有历史消息 provider 回填迁移。

- [x] **Step 3: Add migration**

新增迁移 SQL：从 `setting` 候选池读取 `model -> provider` 映射，只对 `COUNT(DISTINCT provider)=1` 的唯一模型名更新 `chat_message.provider='openai-compatible'` 的历史记录。

- [x] **Step 4: Update feature doc**

补充说明内部客户端编码和对外 provider metadata 的边界，明确 `chat_message.provider` 记录候选池 provider。

- [x] **Step 5: Run backend validation**

Run: `cd backend && mvn -Dtest=AiModelDispatchServiceTest test`

Expected: PASS。

Run: `cd backend && mvn compile`

Expected: PASS。

Run: `cd backend && mvn test`

Expected: PASS，若失败来自其他会话无关改动，按 AGENTS.md 停止并说明。
