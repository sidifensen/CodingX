# Acceptance Criteria: MCP调用面板实时状态与原始信息增强

**Spec:** `docs/superpowers/specs/2026-05-21-220500-mcp-call-panel-realtime-and-raw-design.md`
**Date:** 2026-05-21
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | MCP 调用开始时前端应立即收到可消费的 `mcp-call` 开始事件 | API | 触发一条命中 MCP 意图的流式对话 | SSE 中出现 `event:mcp-call` 且 `phase=start`，包含 `callId` 与 `params` |
| AC-002 | MCP 调用完成时应发送结束事件并携带原始结果与元数据 | API | 同 AC-001 | SSE 中出现 `event:mcp-call` 且 `phase=complete`，与开始事件 `callId` 一致，含 `rawResult/resultMetadata` |
| AC-003 | 前端应按 `callId` 合并 start/complete 事件，而非重复追加两条调用卡片 | Logic | `useChatWorkspace` 消费一组 start/complete 事件 | 对应助手消息 `mcpCalls.length` 保持为 1，状态由 `running` 变为 `completed` |
| AC-004 | 前端收到 start 事件后即展示 MCP 调用中状态，不等待助手最终完成 | UI interaction | 聊天页正在流式回复且收到 `mcp-call start` | MCP 面板状态徽标显示“调用中” |
| AC-005 | MCP 面板调用项应优先展示参数与原始结果，不再仅依赖“输入/返回”重复文案 | UI interaction | 聊天页存在 MCP 调用记录（含 params/rawResult） | 面板出现“参数”“原始结果”字段并可见结构化内容 |
| AC-006 | MCP 面板应展示可读的工具元数据块，支持对象 JSON 格式化 | UI interaction | 调用记录包含 metadata/resultMetadata | 面板出现“元数据”字段，内容为可读 JSON 文本 |
| AC-007 | 对旧格式无 `callId/phase` 的 MCP 事件仍保持可展示（兼容） | Logic | 前端收到仅含旧字段的 `mcp-call` | 调用面板仍追加并展示该调用，不抛异常 |
