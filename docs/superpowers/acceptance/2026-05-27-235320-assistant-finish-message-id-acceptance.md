# Acceptance Criteria: AI 回复完成事件真实消息 ID

**Spec:** `docs/superpowers/specs/2026-05-27-235320-assistant-finish-message-id-design.md`
**Date:** 2026-05-27
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 云端助手消息落库后，`finish` SSE 事件携带真实助手消息 ID。 | Logic | 后端调用 `publishAssistantCompleted(conversationId, assistantMessageId, content, title)`。 | `finish` 载荷包含 `assistantMessageId`，且值等于传入的消息 ID。 |
| AC-002 | 前端收到带 `assistantMessageId` 的 `finish` 后，立即将临时助手消息替换为真实消息 ID。 | Logic | 前端存在 `optimistic-assistant-*` 流式消息，SSE 收到 `finish` 事件。 | `messages` 中该助手消息 ID 变为数字 ID，状态结束，内容为最终内容。 |
| AC-003 | 本地临时会话或无落库消息场景不伪造助手消息 ID。 | Logic | 后端调用三参兼容完成事件或前端收到不含 `assistantMessageId` 的 `finish`。 | 事件载荷不包含 `assistantMessageId`；前端保留原临时 ID。 |

## Self Review

- 验收标准均可由单元测试判定。
- 没有浏览器视觉变更，不需要 CDP 截图。
- 不涉及数据库结构或缓存变更。
