# CLI Codex Style Start Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 CodingX TUI 启动页改成类似官方 Codex CLI 的紧凑终端布局：顶部信息卡、Tip、单行输入提示和简洁底部状态。

**Architecture:** 保留 tui4j `CodingXTuiModel` 状态机和后端 SSE 事件流边界，只调整渲染层和启动方式。启动器不再进入 alt screen，模型初始态不再用大高度 viewport 占位，只有真实 transcript 事件出现时才渲染滚动区。

**Tech Stack:** Java 21、tui4j、JUnit 5、PowerShell 安装脚本。

---

### Task 1: Lock Codex Style Layout With Tests

**Files:**
- Modify: `cli/src/test/java/com/codingx/cli/tui/CodingXTuiModelTest.java`
- Create: `cli/src/test/java/com/codingx/cli/tui/CodingXTuiLauncherTest.java`

- [x] **Step 1: Write failing start screen test**

Assert the initial view contains a bordered `>_ CodingX CLI (v0.1.0)` card, `model:` / `directory:` rows, a `Tip:` line, and the prompt placeholder `Write tests for @filename`.

- [x] **Step 2: Write failing launcher test**

Assert `CodingXTuiLauncher` source no longer calls `withAltScreen()`, because the desired layout keeps the shell command context visible like official Codex CLI.

- [x] **Step 3: Run RED**

Run: `cd cli && mvn "-Dtest=CodingXTuiModelTest,CodingXTuiLauncherTest" test`

Expected: FAIL before implementation because the current view still uses the old startup text, framed composer, and alt screen.

### Task 2: Implement Compact Startup UI

**Files:**
- Modify: `cli/src/main/java/com/codingx/cli/tui/CodingXTuiModel.java`
- Modify: `cli/src/main/java/com/codingx/cli/tui/TuiHeaderRenderer.java`
- Modify: `cli/src/main/java/com/codingx/cli/tui/TuiStatusBarRenderer.java`
- Modify: `cli/src/main/java/com/codingx/cli/tui/CodingXTuiLauncher.java`

- [x] **Step 1: Render the Codex style card**

Render a fixed-width bordered card with product, version, backend-selected model routing, and compact workspace directory.

- [x] **Step 2: Remove initial blank viewport space**

Do not append a startup transcript line. Skip the viewport entirely until `timelineLines` has real user, assistant, tool, or error content.

- [x] **Step 3: Use a single-line composer**

Set textarea height to 1, prompt to `› `, placeholder to `Write tests for @filename`, and remove the old composer border.

- [x] **Step 4: Stop using alt screen**

Change `CodingXTuiLauncher` to call `program.run()` so the previous shell prompt and `codingx` command remain visible.

- [x] **Step 5: Run GREEN**

Run: `cd cli && mvn "-Dtest=CodingXTuiModelTest,CodingXTuiLauncherTest" test`

Expected: PASS after the compact layout is implemented.

### Task 3: Verify, Install, And Document

**Files:**
- Modify: `docs/features/agent/java-cli-terminal-mvp.md`
- Modify: `docs/superpowers/memory/cli/codingx-cli-tui-module-card.md`

- [x] **Step 1: Update docs**

Document the Codex style start screen, normal-screen launcher behavior, and no-blank-viewport invariant.

- [x] **Step 2: Run full CLI verification**

Run: `cd cli && mvn test && mvn package`, then reinstall with `script/install-codingx.ps1`.

- [x] **Step 3: Relaunch through `codingx`**

Stop old TUI windows and start a new visible PowerShell window from `D:\code\CodingX` using `codingx`.

Expected: The new window starts with the compact Codex style page.
