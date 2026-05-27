# Acceptance Criteria: Chat Interleaved Process

**Spec:** `docs/superpowers/specs/2026-05-27-132830-chat-interleaved-process-design.md`
**Date:** 2026-05-27
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 流式正文、工具事件、后续正文按 SSE 到达顺序保存到助手消息时间线。 | Logic | 测试中依次发送 `message`、`tool-call start`、`tool-call complete`、`message` 事件。 | 助手消息 `timelineItems` 顺序为正文片段、过程片段、过程片段、正文片段，且 `content` 仍为两段正文拼接结果。 |
| AC-002 | 带时间线的助手消息按正文和过程节点交错渲染。 | UI interaction | 构造一条含 `timelineItems` 的助手消息，第一段正文后有工具过程，工具过程后有第二段正文。 | DOM 文本顺序中第一段正文早于工具动作，工具动作早于第二段正文，且不再额外在消息顶部重复渲染完整 `processCards`。 |
| AC-003 | 连续网页搜索和连续命令过程在时间线内仍保留现有汇总展示。 | UI interaction | 构造连续搜索结果或连续 shell 命令的过程片段。 | 时间线中显示 `已搜索网页 N 次` 或 `已运行 N 条命令`，展开后能看到对应明细。 |
| AC-004 | 没有 `timelineItems` 的历史消息继续兼容旧渲染。 | UI interaction | 构造仅包含 `processCards` 与 `content` 的历史助手消息。 | 历史消息仍展示过程链路和正文，不出现空白或运行时报错。 |
| AC-005 | 前端构建与测试通过，并完成浏览器验证截图。 | UI interaction | 完成实现后运行验证命令并打开聊天页。 | `npm run test:run`、`npm run build` 退出码为 0，`logs/` 下保存聊天页面截图。 |
