---
type: module_card
title: chat-message-process-trace
summary: 定义聊天主消息区过程链路的前后端职责边界
tags:
  - chat
  - frontend
  - backend
owned_paths:
  - frontend/user/src/views/chat/types.ts
  - frontend/user/src/views/chat/useChatWorkspace.ts
  - frontend/user/src/views/ChatView.tsx
  - backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java
related_docs:
  - docs/superpowers/memory/chat/message-process-timeline-contract.md
  - docs/superpowers/memory/lessons/stream-replay-stale-closure-overwrites-content.md
entrypoints:
  - frontend/user/src/views/chat/useChatWorkspace.ts
  - frontend/user/src/views/ChatView.tsx
  - backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java
last_verified_commit: 6576a5b0d243648431c1165c9c38bbfefa0fe8c2
status: active
---

# Responsibilities

- 把聊天过程中分散的 `thinking / mcp-call / step / reference / message delta` 收敛成主消息区内的统一过程链路
- 维持“过程卡片”和“最终 Markdown 正文”两条并行但同消息上下文的展示路径
- 在 MCP 命中时，确保工具结果回灌模型，再由模型生成最终回答

# Entry points

- `useChatWorkspace.ts`
  - 流式事件消费、消息运行时状态聚合、本地快照回放
- `ChatView.tsx`
  - 主消息区渲染、过程链路折叠交互、暗色主题表现
- `ChatApplicationService.java`
  - MCP 工具执行编排、搜索编排、模型最终回答生成

# Invariants

- 右侧执行回放不再是聊天主链路的可视反馈载体
- 主消息区过程链路采用 WorkBuddy 式内联段落和轻量工具组，不再使用厚重卡片墙或固定时间线
- 分析摘要必须允许自然多行换行，不能被单行容器截断或循环滚动
- `tool_call` 与 `tool_result` 竖向并列展示，参数和结果默认折叠，但必须允许在同一个工具组中展开查看
- 无信息量的合成节点必须过滤，尤其是“整理结论 / 正在整理最终回答 / 已恢复历史上下文”类占位文案
- MCP 工具结果不能直接等价于最终回答

# Extension points

- 新的工具类型可以继续复用 `tool_call / tool_result` 两段过程
- 若后续引入统一 `process-card` SSE，可替换前端当前的运行时聚合实现

# Common pitfalls

- 仅改前端 UI 而不修改 MCP 后端链路，会导致过程链路看起来像“思考 -> 工具 -> 回答”，但回答仍然是工具原文
- 只依赖数据库消息回放会丢失运行时过程链路，需要优先利用本地快照和旧面板数据兜底
- 流式收敛阶段不能直接读取旧闭包里的 `messages`，否则刚完成的 assistant 正文可能会被空历史覆盖
