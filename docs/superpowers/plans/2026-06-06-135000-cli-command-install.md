# CLI Command Install Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Windows 用户在任意新终端输入 `codingx` 后直接进入 CodingX TUI，而不是依赖 `mvn exec:java`。

**Architecture:** Maven `package` 阶段生成包含依赖和 `Main-Class` 的 `target/codingx.jar`。安装脚本负责把 jar 复制到用户目录 `~/.codingx/bin`，生成 Windows `codingx.cmd` / `codingx.ps1` 启动器，并把该目录写入用户级 PATH。

**Tech Stack:** Java 21、Maven Shade Plugin、PowerShell、JUnit 5。

---

### Task 1: Distribution Contract Tests

**Files:**
- Create: `cli/src/test/java/com/codingx/cli/CliDistributionTest.java`

- [x] **Step 1: Write failing test for executable shaded jar**

Test reads `cli/pom.xml` and verifies `maven-shade-plugin`, `com.codingx.cli.CodingXCli`, and `target/codingx.jar` are configured.

- [x] **Step 2: Write failing test for Windows installer**

Test reads `script/install-codingx.ps1` and verifies it creates `.codingx\bin`, `codingx.cmd`, `codingx.ps1`, updates user PATH, and launches with `java -jar`.

- [x] **Step 3: Run RED**

Run: `cd cli && mvn "-Dtest=CliDistributionTest" test`

Expected: FAIL before implementation because the shade plugin and install script do not exist.

### Task 2: Package And Install Implementation

**Files:**
- Modify: `cli/pom.xml`
- Create: `script/install-codingx.ps1`

- [x] **Step 1: Configure shaded jar**

Add `maven-shade-plugin` to `cli/pom.xml`, bind it to `package`, set `Main-Class` to `com.codingx.cli.CodingXCli`, and output `target/codingx.jar`.

- [x] **Step 2: Add Windows install script**

Create `script/install-codingx.ps1` that runs `mvn -q package`, copies `target/codingx.jar` to `~/.codingx/bin/codingx.jar`, writes `codingx.cmd` and `codingx.ps1`, then updates user PATH.

- [x] **Step 3: Run GREEN**

Run: `cd cli && mvn "-Dtest=CliDistributionTest" test`

Expected: PASS after the package and install contracts exist.

### Task 3: Install, Verify, And Document

**Files:**
- Modify: `docs/features/agent/java-cli-terminal-mvp.md`
- Modify: `docs/superpowers/memory/cli/codingx-cli-tui-module-card.md`

- [x] **Step 1: Install locally**

Run: `powershell -ExecutionPolicy Bypass -File script/install-codingx.ps1 -ProjectRoot D:\code\CodingX`

Expected: `where codingx` resolves to `C:\Users\x\.codingx\bin\codingx.cmd`.

- [x] **Step 2: Run full verification**

Run: `cd cli && mvn test && mvn package`, then verify `java -jar target/codingx.jar exec smoke-test` returns the TUI-only message.

- [x] **Step 3: Relaunch via installed command**

Stop the old TUI window and open a new visible terminal from `D:\code\CodingX` with `codingx`.

Expected: The visible terminal enters the current CodingX TUI.
