## Summary
- Scope: 用户端聊天过程时间轴的最小记忆基线
- Result: done
- Created docs: 3
- Updated docs: 1
- Major gaps: 2

## Coverage created
- Modules: `docs/superpowers/memory/module-chat-runtime-rendering.md`
- Contracts: `docs/superpowers/memory/contract-chat-stream-display.md`
- Decisions:
- Runbooks:
- Lessons:
- Index pages: `docs/superpowers/memory/index.md`

## Uncertain or missing areas
- Gap: 后端是否会新增独立 `process-note` 事件尚未确定，目前默认以前端映射现有事件实现。
- Gap: 右栏“参考信息/任务产物”与主时间轴的联动关系尚未形成独立 contract。

## Recommended next scope
- 在完成本次前端改造后，补一份聊天 SSE 事件到用户态过程节点的稳定 contract。
