---
type: contract
title: chat-message-process-trace-contract
summary: 约束聊天主消息区过程链路与现有 SSE 事件之间的映射关系
tags:
  - chat
  - sse
owned_paths:
  - frontend/user/src/views/chat/useChatWorkspace.ts
  - backend/src/main/java/com/codingx/chat/domain/port/ChatStreamPublisher.java
related_docs:
  - docs/superpowers/memory/chat/message-process-timeline-module-card.md
  - docs/superpowers/memory/lessons/stream-replay-stale-closure-overwrites-content.md
entrypoints:
  - frontend/user/src/views/chat/useChatWorkspace.ts
last_verified_commit: 6576a5b0d243648431c1165c9c38bbfefa0fe8c2
status: active
---

# Scope

该契约限定“主消息区过程链路”如何消费当前已有 SSE 事件，而不要求数据库新增字段。

# Producers and consumers

- Producer:
  - `ChatApplicationService.java`
  - `SseChatStreamPublisher.java`
- Consumer:
  - `useChatWorkspace.ts`
  - `ChatView.tsx`

# States and mapping rules

- `analysis`
  - 提交消息后立即创建
  - 收到首个工具开始事件或正文开始整理时转为 `completed`
- `tool_call`
  - `mcp-call start/progress`
  - `step(search)` 视为搜索工具开始
- `tool_result`
  - `mcp-call complete`
  - `reference` 事件或搜索结果聚合
- `synthesis`
  - 仅保留有业务信息量的真实整理摘要
  - 历史会话中的“整理结论 / 正在整理最终回答”占位节点必须在渲染层过滤

# Invariants

- 参数与结果都必须可序列化为文本细节，供折叠面板展示
- 最终正文不放入过程卡片，而继续使用消息 Markdown 正文
- 搜索和 MCP 虽然来源不同，但在主消息区都映射为同一类过程语义
- 主消息区不再使用厚重卡片时间线，不展示“已完成/进行中”这类固定状态文案作为主体内容
- 工具调用与工具结果竖向并列展示，参数和结果默认折叠到同一个轻量工具组里

# Compatibility notes

- 历史消息若无 `processCards`，允许由 `mcpCalls / searchProgress / executionSteps / references` 派生
- 旧格式 `mcp-call` 无 `callId` 时仍需可展示，但精细合并能力下降
- 回放时必须优先选择信息量更高的过程链路，避免刷新后退化为“已恢复历史上下文”类占位文案
- 流结束后立刻回放同一会话时，空的历史正文不得覆盖同一会话里更完整的本地流式正文；此时只应回填面板字段，不应抹掉已生成内容
