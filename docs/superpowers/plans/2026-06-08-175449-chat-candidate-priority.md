# Chat Candidate Priority Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复普通聊天未按管理端候选池 priority 选择模型的问题，让未显式选模型时第一优先级候选真正成为默认调用目标。

**Architecture:** `AiModelSelector` 保留显式 `preferredModel` 插队、图片附件视觉筛选和深度思考请求的 thinking 能力筛选；普通请求不再把 `supports_thinking=true` 候选整体延后。候选是否输出 thinking 仍由 `AiConversationRequest.thinkingEnabled()` 控制，provider 客户端和调度层已有过滤兜底。

**Tech Stack:** Java 21、Spring Boot、JUnit 5、Maven。

---

### Task 1: 用测试锁定候选池 priority 语义

**Files:**
- Modify: `backend/src/test/java/com/codingx/support/ai/AiModelSelectorTest.java`

- [x] **Step 1: Write the failing test**

将普通请求排序测试改为：当 `supports_thinking=true` 候选 priority 更高时，`selectChatCandidates(null, false)` 也应按 priority 返回该候选首位。

- [x] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -Dtest=AiModelSelectorTest#selectChatCandidatesUsesPriorityWhenThinkingDisabled test`

Expected: FAIL，当前实现会先返回非 thinking 候选。

### Task 2: 移除普通请求的 thinking 候选延后逻辑

**Files:**
- Modify: `backend/src/main/java/com/codingx/common/support/ai/AiModelSelector.java`

- [x] **Step 1: Write minimal implementation**

删除排序链路中的 `shouldDeferThinkingCandidate(...)` 比较项，并移除对应私有方法。保留深度思考请求筛选 `supportsThinking=true` 的逻辑。

- [x] **Step 2: Run focused test**

Run: `cd backend && mvn -Dtest=AiModelSelectorTest#selectChatCandidatesUsesPriorityWhenThinkingDisabled test`

Expected: PASS。

### Task 3: 更新文档并验证

**Files:**
- Modify: `docs/features/chat/ai-model-failover.md`
- Modify: `docs/features/chat/ai-routing-defaults.md`
- Modify: `docs/superpowers/memory/chat/ai-routing-module-card.md`
- Modify: `docs/superpowers/memory/chat/ai-routing-selection-contract.md`

- [x] **Step 1: Update docs**

把“普通请求先尝试非 thinking 候选”的旧说明改成“普通请求按 priority 排序，thinking 展示由请求开关控制”。

- [x] **Step 2: Run backend validation**

Run: `cd backend && mvn -Dtest=AiModelSelectorTest,AiModelDispatchServiceTest test`

Expected: PASS。

Run: `cd backend && mvn compile`

Expected: PASS。

Run: `cd backend && mvn test`

Expected: PASS；若失败来自其他会话无关改动，按 AGENTS.md 停止并说明。
