# Acceptance Criteria: 聊天实时代理流式过程

**Spec:** `docs/superpowers/specs/2026-05-27-193859-chat-live-agent-stream-design.md`
**Date:** 2026-05-27
**Status:** Draft

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 工具模式下模型在工具调用前输出的正文会被实时发布并保留到最终 assistant 正文。 | Logic | 使用后端单测模拟模型第一轮依次产生正文 delta 和工具调用，工具执行后第二轮继续输出正文。 | SSE 事件中包含工具前正文 `message`，最终保存的 assistant 内容同时包含工具前正文和工具后正文。 |
| AC-002 | 工具调用不会清空已经发布的 thinking 内容。 | Logic | 使用后端单测模拟深度思考开启，第一轮模型产生 thinking delta、正文 delta 和工具调用。 | 保存的 assistant `thinkingContent` 包含工具调用前的真实 thinking，且不会因进入工具回灌轮次被置空。 |
| AC-003 | 工具执行结果会回灌给下一轮模型，而不是作为最终回答直接返回。 | Logic | 使用后端单测让工具返回固定内容，并让第二轮模型基于该内容输出总结。 | 最终 assistant 正文等于模型第二轮总结与之前正文的累积结果，不等于工具原始输出全文。 |
| AC-004 | 前端按 `message -> tool-call start -> tool-call complete -> message` 的 SSE 到达顺序构建消息时间线。 | Logic | 使用 `useChatWorkspace` 测试注入同一条 assistant 消息的四类 SSE 事件。 | `timelineItems` 顺序依次为正文片段、工具过程节点、工具结果节点、后续正文片段。 |
| AC-005 | `finish` 事件不会重建或覆盖已有时间线导致工具前正文丢失。 | Logic | 在前端 Hook 测试中先构建包含正文、工具、正文的 `timelineItems`，再发送带 `content` 的 `finish` 事件。 | `timelineItems` 中已有片段顺序保持不变，缺失正文只在没有正文片段时补齐。 |
| AC-006 | 渲染层按时间线顺序展示正文、工具节点和后续正文。 | UI interaction | 使用 ChatView 测试或浏览器验证打开包含交错 `timelineItems` 的会话。 | DOM 中文本和工具节点的出现顺序与 `timelineItems` 一致，工具节点不固定堆叠到最终回答上方或下方。 |
| AC-007 | provider 不返回 thinking 时，前端不显示伪造的深度思考内容。 | Logic | 使用前端测试注入仅有 `message` 和 `tool-call` 的 SSE 事件，不注入 `thinking`。 | 消息中没有新增 `analysis` thinking 过程卡片，除非后端真实发送 `thinking` 事件。 |
| AC-008 | 工具执行失败时，失败前已经到达的正文和 thinking 仍保留。 | Logic | 使用后端单测模拟模型输出正文和 thinking 后发起工具调用，工具执行抛出异常。 | SSE 包含工具错误事件和 error 事件，最终错误状态不会清空失败前已发布的正文与 thinking 缓冲。 |
| AC-009 | 工具轮次超过上限时，已有过程片段保留并返回中文错误提示。 | Logic | 将工具轮次上限设为 1，模拟模型连续请求工具。 | 后端设置流错误为 `本地工具调用轮次超过上限，请收敛工具调用后重试`，前端消息保留上限前已到达的正文和工具节点。 |
| AC-010 | 历史回放不会用空历史正文覆盖刚完成的本地流式正文。 | Logic | 使用现有回放相关测试模拟 finish 后立即选择同一会话，历史接口返回空 assistant 正文。 | 本地消息正文仍保留流式生成内容，过程字段只做补充合并。 |
| AC-011 | 功能文档记录当前真实实现。 | Logic | 开发完成后检查 `docs/features/chat/interleaved-process-timeline.md`。 | 文档说明工具前正文保留、工具后正文追加、finish 不覆盖时间线这三项行为。 |
| AC-012 | 前后端改动通过项目规定验证。 | API | 完成实现后在对应目录执行验证命令。 | `mvn compile`、`mvn test`、`npm run build`、`npm run test:run` 均成功，若前端展示有变更则 `logs/` 中包含 CDP 截图或计算样式证据。 |
