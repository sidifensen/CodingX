---
type: decision
title: chat-memory-index
summary: 记录聊天主消息区过程时间线相关的最小仓库记忆入口
tags:
  - chat
owned_paths:
  - frontend/user/src/views/chat
  - frontend/user/src/views/ChatView.tsx
  - backend/src/main/java/com/codingx/chat/application/service/chat
related_docs:
  - docs/superpowers/memory/chat/message-process-timeline-module-card.md
  - docs/superpowers/memory/chat/message-process-timeline-contract.md
  - docs/superpowers/memory/tool/codex-local-tool-runtime-module-card.md
  - docs/superpowers/memory/tool/codex-local-tool-runtime-contract.md
last_verified_commit: 3b6ac022
status: active
---

# Repository Memory

当前已覆盖的聊天域记忆：

- `docs/superpowers/memory/chat/message-process-timeline-module-card.md`
  - 聊天主消息区过程时间线的职责边界与扩展点
- `docs/superpowers/memory/chat/message-process-timeline-contract.md`
  - 主消息区过程卡片与 SSE 事件映射契约
- `docs/superpowers/memory/tool/codex-local-tool-runtime-module-card.md`
  - Codex 风格本地工具执行器的职责、入口与常见陷阱
- `docs/superpowers/memory/tool/codex-local-tool-runtime-contract.md`
  - 本地工具可见性、执行目录、输出和不可用状态契约

当前主要缺口：

- MCP / 搜索 / 思考统一事件模型仍以前端运行时聚合为主，未形成数据库级持久化协议
- 聊天页历史回放和本地快照的长期演化规则尚未独立成 runbook
- 模型 tool-call 流程与本地工具执行结果回灌仍需实现端到端契约
