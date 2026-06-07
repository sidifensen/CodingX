# CLI TUI 登录与 Slash Command 记忆更新报告

## Summary

- Result: updated
- Source spec: none
- Source context: 用户反馈 CLI/TUI 中旧占位符、斜杠命令、登录退出与未登录请求兜底异常
- Source design: none
- Formal commits: base `c3ec85991b362263cdd92bf712f7a257958e7f43`, implementation commit pending
- Created docs: 1
- Updated docs: 2
- Deferred docs: 0

## Durable updates made

- Module cards: updated `docs/superpowers/memory/cli/codingx-cli-tui-module-card.md` to record the single-line Chinese composer placeholder, local Slash Command ownership, pre-submit login guard, and backend no-token skip rule.
- Contracts: updated `docs/features/agent/java-cli-terminal-mvp.md` to describe `codingx logout`, `codingx auth logout`, `/login`, `/logout`, `/help`, automatic login before normal chat submission, and backend-side unauthenticated request prevention.
- Decisions: kept Slash Command rendering as a plain text TUI panel inside `CodingXTuiModel`, so regular terminals and unit tests share the same behavior without introducing an additional widget layer.
- Runbooks: none.
- Lessons: none.

## Not promoted

- Frontend `ChatView` targeted slash/login tests were used only as regression evidence because this cycle did not change frontend production files.
- Existing unrelated dirty worktree changes were intentionally left out of CLI memory.

## Open gaps

- Gap: once this implementation is committed, update this report or downstream memory metadata with the final commit id if the team requires exact commit anchoring for all memory reports.
