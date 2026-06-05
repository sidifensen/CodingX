# CLI TUI Backend Chat Stream Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Java CLI TUI submit prompts to the existing backend chat SSE endpoint and render the returned stream in the terminal.

**Architecture:** Keep `CliCommandRunner` TUI-only. Add a backend `AgentEventSource` implementation under `cli/src/main/java/com/codingx/cli/backend`, parse SSE events into existing `AgentEvent`, and let `CodingXTuiModel` receive background event messages through tui4j `Program.send(...)`.

**Tech Stack:** Java 21 `HttpClient`, Hutool JSON/URL/string utilities, tui4j, JUnit 5, JDK `HttpServer`.

---

### Task 1: Backend SSE Client Tests

**Files:**
- Create: `cli/src/test/java/com/codingx/cli/backend/BackendChatEventSourceTest.java`
- Modify: `cli/pom.xml`

- [ ] **Step 1: Write failing tests**

Add tests that start a local `HttpServer`, save `CliConfig`, call `BackendChatEventSource.startTurn(...)`, and assert request params/header, event mapping, `lastSessionId` writeback, and non-2xx error extraction.

- [ ] **Step 2: Run RED**

Run: `cd cli && mvn -q -Dtest=BackendChatEventSourceTest test`

Expected: compilation fails because `BackendChatEventSource` does not exist.

- [ ] **Step 3: Add minimal implementation**

Add `StreamingAgentEventSource`, `BackendChatEventSource`, `SseEvent`, `SseEventParser`, and `BackendChatEventMapper`. Use Hutool for JSON parsing and URL encoding, Java `HttpClient` for streaming response.

- [ ] **Step 4: Run GREEN**

Run: `cd cli && mvn -q -Dtest=BackendChatEventSourceTest test`

Expected: all tests in `BackendChatEventSourceTest` pass.

### Task 2: TUI Streaming Update

**Files:**
- Modify: `cli/src/main/java/com/codingx/cli/tui/CodingXTuiModel.java`
- Modify: `cli/src/main/java/com/codingx/cli/tui/CodingXTuiLauncher.java`
- Create: `cli/src/main/java/com/codingx/cli/tui/AgentEventsMessage.java`
- Modify: `cli/src/test/java/com/codingx/cli/tui/CodingXTuiModelTest.java`

- [ ] **Step 1: Write failing tests**

Add a model test that sends `AgentEventsMessage` with assistant and completion events into `update(...)` and asserts transcript/status update. Add a test that errors set status to `error`.

- [ ] **Step 2: Run RED**

Run: `cd cli && mvn -q -Dtest=CodingXTuiModelTest test`

Expected: compilation fails because `AgentEventsMessage` does not exist or `CodingXTuiModel` does not handle it.

- [ ] **Step 3: Add streaming model support**

Give `CodingXTuiModel` a `setProgram(Program)` method, dispatch streaming event sources on a background executor, handle `AgentEventsMessage` in `update(...)`, and set `CodingXTuiLauncher` to create the model before the `Program` and inject the program back into the model.

- [ ] **Step 4: Run GREEN**

Run: `cd cli && mvn -q -Dtest=CodingXTuiModelTest test`

Expected: TUI model tests pass without starting a real terminal.

### Task 3: Production Wiring And Rendering

**Files:**
- Modify: `cli/src/main/java/com/codingx/cli/CodingXCli.java`
- Modify: `cli/src/main/java/com/codingx/cli/tui/TuiTranscriptRenderer.java`
- Modify: `cli/src/test/java/com/codingx/cli/tui/TuiTranscriptRendererTest.java`
- Modify: `docs/features/agent/java-cli-terminal-mvp.md`
- Modify: `docs/superpowers/memory/cli/codingx-cli-tui-module-card.md`

- [ ] **Step 1: Write/update tests**

Update transcript renderer tests so tool display names come from payload fields and completion no longer depends on fake `ToolSearch` only.

- [ ] **Step 2: Run RED**

Run: `cd cli && mvn -q -Dtest=TuiTranscriptRendererTest test`

Expected: at least one assertion fails against the old hardcoded tool renderer.

- [ ] **Step 3: Wire real backend source**

Change `CodingXCli.main` to instantiate `BackendChatEventSource` with `CliConfigStore`; keep `MockAgentEventSource` only for tests. Update transcript renderer to display backend `displayName/toolId`, raw output, queue/reject/error events, and real completion status.

- [ ] **Step 4: Run GREEN**

Run: `cd cli && mvn -q -Dtest=TuiTranscriptRendererTest,CodingXCliSmokeTest,CliCommandRunnerTest test`

Expected: selected tests pass.

### Task 4: Full Verification And Commit

**Files:**
- All files changed in Tasks 1-3.

- [ ] **Step 1: Run full CLI tests**

Run: `cd cli && mvn test`

Expected: Maven exits 0.

- [ ] **Step 2: Verify rejected exec command**

Run: `cd cli && mvn -q exec:java "-Dexec.mainClass=com.codingx.cli.CodingXCli" "-Dexec.args=exec 分析这个项目"`

Expected: Maven exits non-zero and output contains the TUI-only Chinese message.

- [ ] **Step 3: Review diff and preserve unrelated work**

Run: `git diff -- cli docs/features/agent/java-cli-terminal-mvp.md docs/superpowers/memory/cli/codingx-cli-tui-module-card.md docs/superpowers/specs/2026-06-06-010700-cli-tui-backend-chat-stream-design.md docs/superpowers/acceptance/2026-06-06-cli-tui-backend-chat-stream.md docs/superpowers/plans/2026-06-06-010700-cli-tui-backend-chat-stream.md`

Expected: diff only includes this task's CLI/docs changes.

- [ ] **Step 4: Commit**

Stage only this task's files and commit with `feat(cli): 对接后端聊天流`。
