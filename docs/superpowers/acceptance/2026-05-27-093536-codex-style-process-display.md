# Acceptance Criteria: Codex Style Process Display

**Spec:** `docs/superpowers/specs/2026-05-27-093536-codex-style-process-display-design.md`
**Date:** 2026-05-27
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | Assistant messages with real thinking content show a default-expanded thinking section. | UI interaction | Render `ChatView` with an assistant message containing an `analysis` process card. | The thinking text is visible before any click, and clicking the thinking toggle hides it. |
| AC-002 | Consecutive web search process results are collapsed into a Codex-style summary row. | UI interaction | Render `ChatView` with at least two search `tool_result` process cards. | The panel shows `已搜索网页 2 次`; individual source summaries are hidden until the summary row is expanded. |
| AC-003 | Expanded web search summary exposes source details and links. | UI interaction | AC-002 setup with each search card containing title, site, and URL details. | After expanding the summary row, each source title is visible and any URL source renders as an external link. |
| AC-004 | Consecutive shell command tool calls are collapsed into a Codex-style command summary row. | UI interaction | Render `ChatView` with two shell command call/result pairs. | The panel shows `已运行 2 条命令`; individual command output is hidden until the command summary is expanded. |
| AC-005 | Expanded shell command summary renders command and output in a shell-style block. | UI interaction | AC-004 setup with command parameters and raw result details. | After expanding the command summary, each command string and result output are visible in command detail blocks. |
| AC-006 | Non-shell tools keep the existing inline tool row behavior. | UI interaction | Render `ChatView` with a weather or MCP process card. | The panel renders the existing individual `process-tool-row-*` rows and does not show `已运行 N 条命令`. |
| AC-007 | Long task process display remains readable in dark mode. | UI interaction | Run the user frontend in dark theme with a long process chain containing thinking, search, and shell cards. | A CDP screenshot shows non-transparent process containers, readable text, and no overlapping input bar. |
