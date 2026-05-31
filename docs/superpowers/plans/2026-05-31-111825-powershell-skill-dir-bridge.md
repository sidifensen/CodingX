# PowerShell Skill Dir Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 Windows 下 `web-access` 技能脚本路径被解析成 `D:\scripts\check-deps.mjs` 的问题。

**Architecture:** 保持技能目录仍由 `ChatToolExecutionContext` 注入为进程环境变量，在 Windows PowerShell 启动命令前增加一层变量桥接，把 `CLAUDE_SKILL_DIR*` 环境变量同步为同名 PowerShell 变量。这样技能文档里的 `${CLAUDE_SKILL_DIR}/scripts/...` 与 `$env:CLAUDE_SKILL_DIR` 两种写法都可用。

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, Maven, Windows PowerShell.

---

### Task 1: 回归测试

**Files:**
- Modify: `backend/src/test/java/com/codingx/tool/application/service/CodexBuiltinChatToolExecutorTest.java`

- [x] **Step 1: Write the failing test**

新增 `shellCommandShouldResolveSkillDirectoryWithBraceVariableSyntax`，创建模拟 `web-access/scripts/check-deps.mjs`，绑定 `ChatToolExecutionContext.bindSkillDirectories(...)` 后执行技能文档同款 `${CLAUDE_SKILL_DIR}/scripts/check-deps.mjs` 路径检查。

- [x] **Step 2: Run test to verify current failure**

Run: `cd backend && mvn -Dtest=CodexBuiltinChatToolExecutorTest#shellCommandShouldResolveSkillDirectoryWithBraceVariableSyntax test`

Actual: Maven 在 `testCompile` 阶段被无关的 `AdminChatSkillServiceTest` 与 `AdminChatSkillControllerTest` 阻塞，原因是它们仍调用旧 3 参数 `uploadSkillPackage(...)` 签名；本轮不修改这些无关测试。

### Task 2: 生产修复

**Files:**
- Modify: `backend/src/main/java/com/codingx/tool/application/service/CodexBuiltinChatToolExecutor.java`

- [x] **Step 1: Bridge PowerShell skill variables**

在 `resolveShellCommand(...)` 的 Windows 分支中包装命令，先枚举 `Env:CLAUDE_SKILL_DIR*` 并通过 `Set-Variable` 写入同名 PowerShell 变量，再执行原始命令。

- [x] **Step 2: Run verification**

Run: `cd backend && mvn -DskipTests compile`

Actual: `mvn -DskipTests compile` 主代码编译通过；`git diff --check` 通过。定向测试仍在 `testCompile` 阶段被无关的 `AdminChatSkillServiceTest` 与 `AdminChatSkillControllerTest` 旧 3 参数 `uploadSkillPackage(...)` 调用阻塞。另用 PowerShell 直接验证桥接命令后，`${CLAUDE_SKILL_DIR}` 能正确解析为环境变量路径。

### Task 3: 文档同步与提交

**Files:**
- Modify: `docs/features/chat/local-tool-runtime.md`
- Create: `docs/superpowers/plans/2026-05-31-111825-powershell-skill-dir-bridge.md`

- [x] **Step 1: Update feature doc**

记录 Windows PowerShell 会桥接 `CLAUDE_SKILL_DIR*`，用于兼容技能文档里的 bash 风格 `${CLAUDE_SKILL_DIR}` 路径。

- [x] **Step 2: Commit only relevant changes**

只提交工具执行器、回归测试、功能文档和计划文档，不纳入其他会话的脏改。提交信息使用 `fix(tool): 兼容技能目录变量路径`。
