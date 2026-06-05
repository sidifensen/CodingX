# MewCode Style CLI TUI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 CodingX Java CLI TUI 升级为接近 MewCode 截图的 header、transcript、输入栏和状态栏外壳。

**Architecture:** 保持 `CodingXTuiModel` 作为 tui4j `Model`，新增 focused renderer 负责 header、transcript 和 status bar 文本生成。任务仍只从 TUI 输入框提交，mock `AgentEventSource` 继续提供事件流，TUI renderer 把事件转为截图风格对话和工具状态。

**Tech Stack:** JDK 21、Maven、tui4j、JUnit 5。

---

## File Structure

- Modify `cli/src/main/java/com/codingx/cli/tui/CodingXTuiModel.java`: 增加模型名、计划模式、截图风格布局和按键切换。
- Create `cli/src/main/java/com/codingx/cli/tui/TuiHeaderRenderer.java`: 渲染 ASCII 品牌、版本、模型、工作区和 mock MCP 工具状态。
- Create `cli/src/main/java/com/codingx/cli/tui/TuiTranscriptRenderer.java`: 把 `AgentEvent` 转为截图风格 transcript 行。
- Create `cli/src/main/java/com/codingx/cli/tui/TuiStatusBarRenderer.java`: 渲染 `Plan on/off`、运行状态和右侧模型名。
- Modify `cli/src/test/java/com/codingx/cli/tui/CodingXTuiModelTest.java`: 按 AC 覆盖初始视图、任务提交、错误事件、空白输入和计划模式切换。
- Create `cli/src/test/java/com/codingx/cli/tui/TuiTranscriptRendererTest.java`: 覆盖工具事件、助手事件和错误事件渲染。
- Modify `docs/features/agent/java-cli-terminal-mvp.md`: 更新当前真实实现为截图风格 TUI 外壳。

## Task 1: Header And Status Bar Tests

- [ ] Write failing tests in `CodingXTuiModelTest`:
  - `initialViewShouldShowMewCodeStyleHeaderAndStatusBar`
  - assert `CodingX v0.1.0`
  - assert `GLM-5.1`
  - assert workspace path
  - assert `Connected to 1 MCP server(s), 2 tools registered`
  - assert `Send a message...`
  - assert `Plan on (shift+tab to cycle)`
  - assert `Status: ready`
- [ ] Run `cd cli && mvn -Dtest=CodingXTuiModelTest test` and verify RED because the current model does not render these strings.
- [ ] Implement `TuiHeaderRenderer` and `TuiStatusBarRenderer`.
- [ ] Wire renderers into `CodingXTuiModel.view()`.
- [ ] Run `cd cli && mvn -Dtest=CodingXTuiModelTest test` and verify GREEN for the new initial-view test.

## Task 2: Transcript Renderer Tests

- [ ] Write failing `TuiTranscriptRendererTest`:
  - assistant event renders as indented assistant text.
  - `TOOL_STARTED` renders `ToolSearch`.
  - `TOOL_COMPLETED` renders `(0.0s)`.
  - `TURN_COMPLETED` renders `Task completed: COMPLETED`.
  - `ERROR` renders `! Error`.
- [ ] Run `cd cli && mvn -Dtest=TuiTranscriptRendererTest test` and verify RED because the renderer does not exist.
- [ ] Implement `TuiTranscriptRenderer` with comments explaining mock-duration constraints.
- [ ] Run `cd cli && mvn -Dtest=TuiTranscriptRendererTest test` and verify GREEN.

## Task 3: Model Submission And Plan Mode

- [ ] Extend `CodingXTuiModelTest` with failing tests:
  - `submitTaskShouldAppendMewCodeStyleTranscript`
  - `blankTaskShouldNotChangeTranscriptOrStatus`
  - `shiftTabShouldTogglePlanMode`
  - `errorEventsShouldSetErrorStatus`
- [ ] Run `cd cli && mvn -Dtest=CodingXTuiModelTest test` and verify RED.
- [ ] Update `CodingXTuiModel`:
  - add `modelName = "GLM-5.1"`
  - add `planMode = true`
  - toggle plan mode on `shift+tab`
  - use `TuiTranscriptRenderer` instead of `TerminalRenderer` for TUI transcript lines
  - keep `TerminalRenderer` constructor dependency only if required by existing launcher compatibility, otherwise remove it from TUI model and launcher
  - append `Synthesizing...` before completed status in mock transcript
  - keep blank task ignored
- [ ] Run `cd cli && mvn -Dtest=CodingXTuiModelTest test` and verify GREEN.

## Task 4: Documentation And Full Verification

- [ ] Update `docs/features/agent/java-cli-terminal-mvp.md` to describe screenshot-style TUI shell and mock limitations.
- [ ] Run `cd cli && mvn test`.
- [ ] Run `cd cli && mvn -q exec:java "-Dexec.mainClass=com.codingx.cli.CodingXCli" "-Dexec.args=exec 分析这个项目"` and verify it exits non-zero with the TUI-only message.
- [ ] Do not run the no-arg interactive TUI in automated verification because it takes over the terminal; rely on model tests for deterministic layout coverage.
