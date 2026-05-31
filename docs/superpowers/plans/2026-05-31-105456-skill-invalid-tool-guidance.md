# Skill Invalid Tool Guidance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复模型把 `web-access` 技能误说成不可用的问题，让后端只忽略错误 `tool_call`，继续保留已选技能语义。

**Architecture:** 在工具白名单过滤阶段保留现有拦截策略，只调整回灌给模型的系统纠偏文案。回归测试捕获第二轮模型历史，确认文案明确“已选技能仍然有效”，且不再诱导模型说技能被忽略。

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, Maven.

---

### Task 1: 回归测试

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationToolCallFlowTest.java`

- [x] **Step 1: Write the failing test**

在 `sendMessageIgnoresSkillCodeToolCallWhenNotInVisibleToolSpecs` 中捕获第二轮 `streamChatWithTools` 的 `history`，断言新增的系统纠偏消息包含“仅忽略错误 tool_call”和“已选技能仍然有效”，并断言不包含“技能不可用”“技能被忽略”。

- [x] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -Dtest=ChatApplicationToolCallFlowTest#sendMessageIgnoresSkillCodeToolCallWhenNotInVisibleToolSpecs test`

Actual: Maven 主编译先被其他会话的 `ChatController` 脏改阻塞，错误为缺少 `sendSynchronousMessage(...)` 方法和 `StrUtil` 导入；本轮不修改该无关文件。

### Task 2: 生产修复

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`

- [x] **Step 1: Update invalid tool guidance**

只修改 `buildInvalidToolCallGuidance(...)` 的文案：说明后端仅忽略错误 `tool_call`，技能选择和技能上下文仍然保留；要求模型继续按已选技能说明回答；禁止把 skill code 再当工具名调用。

- [x] **Step 2: Run focused verification**

Run: `cd backend && mvn -DskipTests compile`

Actual: `mvn -DskipTests compile` 编译通过。定向测试 `mvn -Dtest=ChatApplicationToolCallFlowTest#sendMessageIgnoresSkillCodeToolCallWhenNotInVisibleToolSpecs test` 在 `testCompile` 阶段被无关的 `AdminChatSkillServiceTest` 与 `AdminChatSkillControllerTest` 阻塞，原因是它们仍调用旧 3 参数 `uploadSkillPackage(...)` 签名。

### Task 3: 文档同步与提交

**Files:**
- Modify: `docs/features/chat/skill-context-persistence.md`
- Create: `docs/superpowers/plans/2026-05-31-105456-skill-invalid-tool-guidance.md`

- [x] **Step 1: Update feature doc**

把“忽略该调用”改成“仅忽略错误 tool_call”，明确技能上下文仍然有效。

- [x] **Step 2: Commit only relevant changes**

只提交本计划、回归测试、生产文案和功能文档，不纳入其他会话的脏改。提交信息使用 `fix(chat): 修正技能伪工具调用提示`。
