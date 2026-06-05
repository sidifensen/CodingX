# Acceptance Criteria: MewCode 风格 CLI TUI

**Spec:** `docs/superpowers/specs/2026-06-05-233009-cli-mewcode-style-tui-design.md`
**Date:** 2026-06-05
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | TUI 初始视图展示截图风格 header 信息。 | Logic | 构造 `CodingXTuiModel`，工作区使用临时目录。 | `view()` 同时包含 `CodingX v0.1.0`、`GLM-5.1`、工作区路径、`Connected to 1 MCP server(s), 2 tools registered`。 |
| AC-002 | TUI 初始视图展示固定输入区和底部状态栏。 | Logic | 构造 `CodingXTuiModel`，不提交任务。 | `view()` 包含 `Send a message...`、`Plan on (shift+tab to cycle)`、`Status: ready` 和右侧模型名 `GLM-5.1`。 |
| AC-003 | 提交任务后 transcript 使用对话流展示用户消息和助手消息。 | Logic | 构造 `CodingXTuiModel` 并调用 `submitTask("我想做一个电商系统")`。 | `view()` 包含 `> 我想做一个电商系统`，并包含 mock 助手正文 `我会先查看当前仓库结构`。 |
| AC-004 | 提交任务后工具事件展示为截图风格工具状态行。 | Logic | 构造 `CodingXTuiModel` 并调用 `submitTask("分析这个项目")`。 | `view()` 包含 `ToolSearch`、`(0.0s)`、`Synthesizing...` 和 `Task completed: COMPLETED`，不再把工具主状态展示成 `[tool] 工具: ls`。 |
| AC-005 | 错误事件会在 transcript 和状态栏中体现。 | Logic | 使用测试事件源返回 `ERROR` 事件并提交任务。 | `view()` 包含 `! Error`，底部状态包含 `Status: error`。 |
| AC-006 | 空白输入不会追加用户消息或改变运行状态。 | Logic | 构造 `CodingXTuiModel` 后调用 `submitTask("   ")`。 | `view()` 不包含仅由空白任务产生的 `> ` 用户消息，状态仍为 `Status: ready`。 |
| AC-007 | `shift+tab` 可以在本地切换 Plan 模式。 | Logic | 构造 `CodingXTuiModel` 并向 `update()` 发送 `shift+tab` 按键消息。 | 首次 `view()` 包含 `Plan on`，发送一次后包含 `Plan off`，再次发送后恢复 `Plan on`。 |
| AC-008 | CLI 仍保持 TUI-only，非交互 `exec` 不会重新成为任务入口。 | API | 在 `cli` 目录运行 `mvn -q exec:java "-Dexec.mainClass=com.codingx.cli.CodingXCli" "-Dexec.args=exec 分析这个项目"`。 | 命令退出码非 0，输出包含 `只支持 TUI 交互模式`。 |
| AC-009 | CLI 子项目测试全部通过。 | Logic | 在 `cli` 目录运行 `mvn test`。 | Maven 输出 `BUILD SUCCESS`，测试结果为 0 failures、0 errors。 |
