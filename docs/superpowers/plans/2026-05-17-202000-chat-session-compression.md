# 会话压缩闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让会话摘要从“仅落库”升级为“真实参与后续入模上下文”，并通过配置控制摘要触发与历史窗口。

**Architecture:** 在聊天应用层引入 `ChatMemoryProperties` 配置，`ConversationSummaryService` 负责增量摘要与上下文裁剪，`ChatApplicationService` 统一接入压缩后历史。整体保持现有意图、搜索、MCP 与 Trace 链路不变。

**Tech Stack:** Spring Boot 3、MyBatis-Plus、JUnit5、Mockito、Hutool

---

### Task 1: 配置与阈值接入

**Files:**
- Create: `backend/src/main/java/com/codingx/config/ChatMemoryProperties.java`
- Modify: `backend/src/main/java/com/codingx/CodingXApplication.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/codingx/chat/application/service/ConversationDigestServiceTest.java`

- [x] **Step 1: 先写失败测试（阈值配置化）**
- [x] **Step 2: 增加 `ChatMemoryProperties` 并注册到 Spring 配置体系**
- [x] **Step 3: 改造 `ConversationDigestService` 支持配置注入构造**
- [x] **Step 4: 运行 `ConversationDigestServiceTest` 确认通过**

### Task 2: 摘要增量压缩与上下文组装

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/application/service/ConversationSummaryService.java`
- Create: `backend/src/test/java/com/codingx/chat/application/service/ConversationSummaryServiceTest.java`

- [x] **Step 1: 先写失败测试（增量压缩截止点 + 摘要注入上下文）**
- [x] **Step 2: 实现增量摘要逻辑（窗口外压缩、`last_message_id` 推进、最大长度保护）**
- [x] **Step 3: 实现 `buildModelHistory`（摘要 system + 最近窗口）**
- [x] **Step 4: 运行 `ConversationSummaryServiceTest` 验证通过**

### Task 3: 主链路接入与回归

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/application/service/ChatApplicationService.java`
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationServiceTest.java`
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationIntentFlowTest.java`
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationSearchFlowTest.java`
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationMcpFlowTest.java`

- [x] **Step 1: 先让相关测试在新调用点下失败（RED）**
- [x] **Step 2: 在主链路接入 `conversationSummaryService.buildModelHistory`**
- [x] **Step 3: 修复测试桩与断言，完成 GREEN**
- [x] **Step 4: 定向运行关键测试集确认通过**

### Task 4: 全量验证与交付

**Files:**
- Modify: `docs/superpowers/specs/2026-05-17-201800-chat-session-compression-design.md`
- Modify: `docs/superpowers/acceptance/2026-05-17-201900-chat-session-compression-acceptance.md`
- Modify: `docs/superpowers/plans/2026-05-17-202000-chat-session-compression.md`

- [x] **Step 1: 执行 `mvn compile`**
- [x] **Step 2: 执行 `mvn test`**
- [x] **Step 3: 同步 spec / acceptance / plan 文档，确保交付可追溯**
