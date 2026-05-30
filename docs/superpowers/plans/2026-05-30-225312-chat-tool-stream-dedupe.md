# Chat Tool Stream Dedupe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 防止本地工具回灌后同一条助手回复重复展示已经流式输出过的正文。

**Architecture:** 在 `ChatApplicationService` 的工具调用循环中增加服务端前缀跳过状态，并在 `buildStreamHandler.onDelta` 发布 SSE 与追加正文前消费重复前缀。测试直接模拟两轮 `streamChatWithTools`，验证 SSE delta 和最终消息内容都不重复。

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito.

---

### Task 1: 后端工具流重复前缀回归测试

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationServiceTest.java`

- [ ] **Step 1: Write the failing test**

新增 `sendMessageSkipsRepeatedPublishedPrefixAfterToolCall`：第一轮模型输出 `我将为你创建 HTML 游戏。` 并请求 `shell_command`；工具返回后第二轮模型再次输出同一前缀，再输出 `\n\n已创建完成。`。断言 `publishAssistantDelta` 只发布一次前缀，最终助手消息内容为 `我将为你创建 HTML 游戏。\n\n已创建完成。`。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=ChatApplicationServiceTest#sendMessageSkipsRepeatedPublishedPrefixAfterToolCall test`

Expected: FAIL，因为当前实现会重复追加前缀。

### Task 2: 服务端工具回灌前缀去重

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`

- [ ] **Step 1: Implement minimal fix**

在工具循环中维护 `AtomicReference<String>` 跳过前缀；当工具轮次结束且 `builder` 已产生新增正文时，把当前完整正文写入该状态。`onDelta` 先调用私有方法消费重复前缀，只发布返回的剩余 delta。

- [ ] **Step 2: Run regression test**

Run: `mvn -Dtest=ChatApplicationServiceTest#sendMessageSkipsRepeatedPublishedPrefixAfterToolCall test`

Expected: PASS。

### Task 3: 验证与收尾

**Files:**
- Verify: `backend`

- [ ] **Step 1: Run backend compile**

Run: `mvn compile`

Expected: BUILD SUCCESS。

- [ ] **Step 2: Run backend tests**

Run: `mvn test`

Expected: BUILD SUCCESS，或明确记录非本次改动导致的失败。
