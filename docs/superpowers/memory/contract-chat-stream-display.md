---
type: contract
title: chat-stream-display
summary: 约束用户端聊天流式事件如何转换为可展示的过程时间轴与最终回答。
tags:
  - chat
  - contract
  - sse
owned_paths:
  - frontend/user/src/views/chat/useChatWorkspace.ts
  - frontend/user/src/views/ChatView.tsx
related_docs:
  - docs/superpowers/memory/module-chat-runtime-rendering.md
entrypoints:
  - frontend/user/src/views/chat/useChatWorkspace.ts
last_verified_commit: 58d17d4a
status: draft
---

## Scope
- 仅覆盖用户端聊天主区的过程展示，不包含调试态、管理端追踪页或原始协议排障视图。

## Producers and consumers
- Producer: `useChatWorkspace` 把 SSE 事件聚合为 `ChatMessageItem` 内的展示字段。
- Consumer: `ChatView` 按固定顺序渲染过程时间轴和最终回答。

## Interface rules
- 用户态过程展示只允许短过程节点、搜索进度节点和最终回答节点。
- 不展示工具编码、请求参数、原始结果、元数据和长篇原样 reasoning 文本。
- 搜索与 MCP 等内部事件应先转换成用户可读文案，再进入时间轴。
- 最终回答必须独立于过程节点渲染，不能混在过程卡片内部。

## Invariants
- 过程节点文案必须短句化，单节点聚焦一个阶段状态。
- 过程节点顺序必须遵循真实事件时间，不能在最终回答后继续追加前置阶段说明。
- 当无过程节点时，最终回答仍可单独渲染。

## Compatibility notes
- 现有 `thinkingContent`、`mcpCalls`、`searchProgress` 仍可作为底层输入来源，但不应继续以原样面板暴露给用户。
