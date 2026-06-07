# CLI 授权白名单记忆更新报告

## Summary

- Result: updated
- Source spec: none
- Source context: 用户反馈浏览器登录后回到终端仍不能对话，根因定位到 Sa-Token 拦截 CLI 授权码兑换接口
- Source design: none
- Formal commits: `6821cb68`
- Created docs: 1
- Updated docs: 1
- Deferred docs: 0

## Durable updates made

- Module cards: updated `docs/superpowers/memory/cli/codingx-cli-tui-module-card.md` to record the CLI auth whitelist boundary and the failure mode where browser authorization succeeds but terminal token exchange is blocked.
- Contracts: none.
- Decisions: keep browser authorization endpoints protected by网页登录态 while whitelisting only terminal anonymous exchange/polling endpoints.
- Runbooks: none.
- Lessons: CLI loopback login must be verified through browser callback, token exchange, backend stream, and real TUI pty output before declaring it fixed.

## Not promoted

- The specific temporary callback port and generated token were left out because they are one-off runtime values.
- E2E logs under `logs/` remain local verification evidence and are not part of canonical memory.

## Open gaps

- Gap: no dedicated runbook exists yet for full CLI login/TUI E2E verification; create one if this flow becomes a recurring release gate.
