# Bootstrap Report: CLI TUI

## Summary

- Scope: `cli/src/main/java/com/codingx/cli`、`cli/src/test/java/com/codingx/cli` 和现有 Java CLI 功能文档。
- Result: done
- Created docs: 1
- Updated docs: 1
- Major gaps: 3

## Coverage Created

- Modules:
  - `docs/superpowers/memory/cli/codingx-cli-tui-module-card.md`
- Contracts:
  - None. 当前 CLI 仍处于 mock 事件源阶段，真实 Agent API / SSE 契约尚未实现。
- Decisions:
  - None. 本次只记录已提交的 TUI-only 边界，不新增架构决策。
- Runbooks:
  - None.
- Lessons:
  - None.
- Index pages:
  - `docs/superpowers/memory/index.md`

## Uncertain Or Missing Areas

- Gap: 真实 Agent Runtime 事件源尚未接入，`MockAgentEventSource` 只用于跑通 TUI 外壳。
- Gap: TUI 会话恢复、会话列表和模式切换还没有独立状态机或持久化契约。
- Gap: MCP 工具、审批事件和工具执行结果只存在事件类型与 mock 展示，没有端到端后端协议。

## Recommended Next Scope

- 最小后续范围应聚焦于 TUI 外观和交互信息架构，把截图风格的 header、transcript、工具状态、输入栏和状态栏拆成可测试 renderer。
