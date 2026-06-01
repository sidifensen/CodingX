# Chat Thinking Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent normal chat requests from selecting or displaying deep-thinking output unless the user explicitly enables deep thinking.

**Architecture:** Keep the existing SSE and process-card UI unchanged. Fix the backend gate at two layers: model selection should prefer non-thinking candidates for normal requests, and provider clients should drop upstream reasoning deltas when `AiConversationRequest.thinkingEnabled()` is false.

**Tech Stack:** Spring Boot, JUnit 5, Hutool JSON/utilities, OkHttp provider clients.

---

### Task 1: Reproduce the Routing Bug

**Files:**
- Modify: `backend/src/test/java/com/codingx/support/ai/AiModelSelectorTest.java`

- [x] **Step 1: Write the failing test**

Add a test proving that a normal request without explicit model preference puts non-thinking candidates before thinking-capable candidates even when the thinking candidate has a smaller priority value.

- [x] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -Dtest=AiModelSelectorTest#selectChatCandidatesPrefersNonThinkingCandidatesWhenThinkingDisabled test`

Expected: FAIL because current sorting returns the thinking-capable candidate first.

### Task 2: Reproduce the Reasoning Leak

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/infrastructure/ai/OpenAiCompatibleChatClientTest.java`
- Modify: `backend/src/test/java/com/codingx/chat/infrastructure/ai/DeepSeekOkHttpChatClientTest.java`

- [x] **Step 1: Write failing provider tests**

Add tests where upstream sends `reasoning_content` while `thinkingEnabled=false`; assert no `onThinkingDelta` is emitted and normal content still streams.

- [x] **Step 2: Run tests to verify they fail**

Run: `cd backend && mvn -Dtest=OpenAiCompatibleChatClientTest#streamChatSuppressesReasoningContentWhenThinkingDisabled,DeepSeekOkHttpChatClientTest#streamChatSuppressesReasoningContentWhenThinkingDisabled test`

Expected: FAIL because both provider clients currently forward all parsed reasoning deltas.

### Task 3: Implement the Fix

**Files:**
- Modify: `backend/src/main/java/com/codingx/common/support/ai/AiModelSelector.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/ai/OpenAiCompatibleChatClient.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/ai/DeepSeekOkHttpChatClient.java`

- [x] **Step 1: Update candidate ordering**

In `AiModelSelector.selectChatCandidates`, keep explicit `preferredModel` first, then for normal requests sort non-thinking candidates before thinking-capable candidates, then apply `priority` and candidate ID.

- [x] **Step 2: Guard thinking forwarding**

In each provider client's `onThinkingDelta`, forward the delta only when the request is not cancelled and `request.thinkingEnabled()` is true.

- [x] **Step 3: Run focused tests**

Run the three failing tests again and confirm they pass.

### Task 4: Document and Verify

**Files:**
- Modify: `docs/features/chat/ai-model-failover.md`
- Modify: `docs/features/chat/ai-routing-defaults.md`

- [x] **Step 1: Update feature documentation**

Document that normal requests prefer non-thinking candidates and suppress upstream reasoning deltas even if a provider returns them.

- [x] **Step 2: Run required backend checks**

Run: `cd backend && mvn compile && mvn test`

Expected: PASS unless unrelated pre-existing worktree changes cause failures; if so, report the blocker and only submit this task's files.

Result: latest `mvn compile` passed. Latest `mvn test` failed during `testCompile` in unrelated `AdminChatConversationServiceTest` calls to missing `ChatMessageResponse.runId()/deleted()/updatedAt()` methods; affected routing/provider tests passed separately.
