---
type: module_card
title: chat-runtime-rendering
summary: 用户端聊天页把流式消息、思考、搜索进度与 MCP 调用聚合为单条助手消息渲染。
tags:
  - chat
  - frontend
  - runtime
owned_paths:
  - frontend/user/src/views/ChatView.tsx
  - frontend/user/src/views/chat/useChatWorkspace.ts
  - frontend/user/src/views/chat/types.ts
related_docs:
  - docs/superpowers/memory/contract-chat-stream-display.md
entrypoints:
  - frontend/user/src/views/ChatView.tsx
  - frontend/user/src/views/chat/useChatWorkspace.ts
last_verified_commit: 58d17d4a
status: active
---

## Responsibilities
- `useChatWorkspace` 消费聊天 SSE 事件，并把增量内容聚合到当前助手消息。
- `ChatView` 按消息内字段顺序渲染思考面板、MCP 面板、搜索进度、附件与最终正文。
- `types.ts` 约束消息回放与流式拼接所需的数据结构。

## Entry points
- `frontend/user/src/views/chat/useChatWorkspace.ts`: 处理 `thinking`、`mcp-call`、`step`、`reference`、`finish` 等事件。
- `frontend/user/src/views/ChatView.tsx`: 负责过程面板与消息正文的组合展示。
- `frontend/user/src/views/chat/types.ts`: 定义 `ChatMessageItem`、`McpCallItem`、`MessageSearchProgress` 等类型。

## Invariants
- 同一条助手消息承载本轮流式会话的过程信息与最终回答。
- `thinkingContent` 目前是原样拼接的长文本，不做结构化拆分。
- `mcpCalls` 通过 `callId` 合并 start/complete 阶段，但视图仍以工具详情卡片方式直接展示。
- 搜索进度由 `step` 与 `reference` 事件共同驱动，并与最终正文并列渲染。

## Extension points
- 可以在消息模型中新增用户态过程节点，替代长思考文本和原始 MCP 详情的直接暴露。
- 可以把搜索、MCP、整理阶段统一映射为时间轴节点，再由 `ChatView` 单组件排序展示。

## Common pitfalls
- 直接把底层工具事件暴露给用户，会与最终回答和右栏来源区重复。
- 仅修改 `ChatView` 不同步更新 `useChatWorkspace` 类型聚合，会导致回放与流式渲染不一致。
- 流式阶段若仍依赖 `thinkingContent` 长文本，容易造成界面噪音与重复说明。
