# CLI TUI Visual Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 优化 CodingX Java CLI TUI 的终端观感，去掉底部假模型和 mock 工具文案，并让流式助手输出合并成自然对话块。

**Architecture:** 继续沿用 tui4j `CodingXTuiModel` 作为状态模型，保留 `TuiHeaderRenderer`、`TuiTranscriptRenderer`、`TuiStatusBarRenderer` 的展示边界。参考官方 OpenAI Codex TUI 的分层：主聊天区维护 transcript，底部 pane 负责输入和状态，footer 只渲染真实状态，不在界面里硬编码不可验证的模型或工具数量。

**Tech Stack:** Java 21、tui4j、JUnit 5、SnakeYAML、Hutool。

---

### Task 1: Lock UI Expectations With Tests

**Files:**
- Modify: `cli/src/test/java/com/codingx/cli/tui/CodingXTuiModelTest.java`
- Modify: `cli/src/test/java/com/codingx/cli/tui/TuiTranscriptRendererTest.java`

- [x] **Step 1: Write failing assertions for the refreshed shell**

Update the initial model test so it expects `CodingX CLI v0.1.0`, the workspace path, the input placeholder, and a status line without `GLM-5.1` or mock MCP text.

- [x] **Step 2: Write failing assertions for coalesced assistant deltas**

Add a model test that sends two `ASSISTANT_DELTA` stream messages and verifies the rendered transcript contains one continuous assistant sentence and no `  • ` bullet prefix.

- [x] **Step 3: Run targeted tests and confirm RED**

Run: `cd cli && mvn -Dtest=CodingXTuiModelTest,TuiTranscriptRendererTest test`

Expected: FAIL because production renderers still output fake model text, mock MCP text, and bullet-prefixed assistant deltas.

### Task 2: Refresh Header, Input, Status, And Assistant Stream Rendering

**Files:**
- Modify: `cli/src/main/java/com/codingx/cli/tui/TuiHeaderRenderer.java`
- Modify: `cli/src/main/java/com/codingx/cli/tui/TuiStatusBarRenderer.java`
- Modify: `cli/src/main/java/com/codingx/cli/tui/TuiTranscriptRenderer.java`
- Modify: `cli/src/main/java/com/codingx/cli/tui/CodingXTuiModel.java`

- [x] **Step 1: Remove fake model and mock MCP header data**

Change `TuiHeaderRenderer.render(...)` to accept only the workspace path and render a compact brand/workspace block. Do not display model names or MCP/tool counts until those values come from real backend state.

- [x] **Step 2: Make the bottom status bar state-only**

Change `TuiStatusBarRenderer.render(...)` to render collaboration mode and run status only. Keep `Shift+Tab` as the local mode switch hint, but remove `modelName`.

- [x] **Step 3: Coalesce assistant deltas in the model**

Track the current assistant block in `CodingXTuiModel`; consecutive `ASSISTANT_DELTA` events append to the same visible block, while tool, command, completion, and error events close the block. This keeps SSE chunks from becoming a vertical bullet list.

- [x] **Step 4: Frame the composer area**

Set the textarea prompt to a quieter gutter and wrap it with a lightweight input frame in `view()`. The frame should use the current terminal width and avoid relying on a fake model label.

- [x] **Step 5: Run tests and confirm GREEN**

Run: `cd cli && mvn -Dtest=CodingXTuiModelTest,TuiTranscriptRendererTest test`

Expected: PASS.

### Task 3: Documentation And Verification

**Files:**
- Modify: `docs/features/agent/java-cli-terminal-mvp.md`
- Modify: `docs/superpowers/memory/cli/codingx-cli-tui-module-card.md`

- [x] **Step 1: Update feature documentation**

Document the refreshed TUI contract: no fake model display, no mock MCP status, state-only footer, and assistant stream coalescing.

- [x] **Step 2: Run full CLI test suite**

Run: `cd cli && mvn test`

Expected: PASS.

- [x] **Step 3: Restart the local TUI**

Stop the old TUI process if it is still running, then start a new visible PowerShell window:

```powershell
Set-Location 'D:\code\CodingX'
mvn -q -f '.\cli\pom.xml' exec:java '-Dexec.mainClass=com.codingx.cli.CodingXCli'
```

Expected: The visible TUI starts with the refreshed shell and no fake model in the footer.
