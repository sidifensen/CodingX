# Web Access CodingX Runtime Guidance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复对象存储版 `web-access` 技能仍诱导模型在 Windows PowerShell 中照搬 `curl --data-raw` 示例的问题。

**Architecture:** `ChatSkillContextService` 继续优先读取对象存储或内置 `SKILL.md`，但当生效技能为 `web-access` 时，在已裁剪的技能说明后追加 CodingX 运行时约束。约束说明当前可执行命令实际由 Windows PowerShell 承载，并给出 `Invoke-RestMethod` / `Invoke-WebRequest -Body` 的 CDP Proxy 调用方式，避免直接修改第三方技能包内容。

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, Maven, RustFS directory skill package.

---

### Task 1: 回归测试

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatSkillContextServiceTest.java`

- [x] **Step 1: Write the failing test**

新增 `buildSkillContextAddsCodingXPowerShellGuidanceForStoredWebAccessManifest`，模拟数据库中的 `web-access` 为对象存储目录技能包，且远程 `SKILL.md` 包含上游 `curl -s -X POST ... --data-raw` 示例。

- [x] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -Dtest=ChatSkillContextServiceTest#buildSkillContextAddsCodingXPowerShellGuidanceForStoredWebAccessManifest test`

Actual: FAIL，测试编译补齐 `StandardCharsets` 后，断言停在缺少 `CodingX 运行时约束`。

### Task 2: 生产修复

**Files:**
- Modify: `backend/src/main/java/com/codingx/skill/application/service/ChatSkillContextService.java`

- [x] **Step 1: Append runtime guidance for web-access**

在 `web-access` 技能上下文中追加固定提示，说明：

- `bash` / `shell_command` 在本项目 Windows 环境中使用 Windows PowerShell；
- 先执行 `node "$env:CLAUDE_SKILL_DIR\scripts\check-deps.mjs"`；
- CDP Proxy GET 用 `Invoke-RestMethod -Uri`；
- CDP Proxy POST 用 `Invoke-WebRequest -Method Post -Body`，不要照搬 `curl --data-raw`；
- 创建 tab 后要继续读取 `/info`、`/eval` 或截图结果，不能只返回 `targetId`。

- [x] **Step 2: Run targeted test to verify it passes**

Run: `cd backend && mvn -Dtest=ChatSkillContextServiceTest#buildSkillContextAddsCodingXPowerShellGuidanceForStoredWebAccessManifest test`

Actual: PASS。

### Task 3: 文档与验证

**Files:**
- Modify: `docs/features/chat/skill-context-persistence.md`

- [x] **Step 1: Update feature documentation**

记录对象存储版 `web-access` 也会追加 CodingX 运行时约束，避免模型按上游 `curl` 示例生成 PowerShell 不兼容命令。

- [x] **Step 2: Run affected backend verification**

Run: `cd backend && mvn -Dtest=ChatSkillContextServiceTest test`

Actual: `mvn -q compile` 和 `mvn -q -Dtest=ChatSkillContextServiceTest test` 通过；`mvn -q test` 被当前工作区已有无关测试失败阻塞，主要包括旧 `publishAssistantCompleted` 签名断言、`ChatStreamControllerTest` 依赖为空、`AdminChatSettingsServiceTest` 加密断言等。

- [x] **Step 3: Commit relevant changes**

只提交本次修改的测试、服务和文档文件。提交信息：`fix(skill): 补充联网技能运行时约束`。
