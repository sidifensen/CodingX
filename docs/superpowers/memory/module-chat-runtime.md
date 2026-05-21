---
type: module_card
title: chat-runtime
summary: CodingX 的聊天运行时将作为统一入口，负责意图路由、流式生成、联网搜索编排、文档产物输出与会话状态管理。
tags:
  - chat
  - runtime
  - search
  - sse
owned_paths:
  - CodingX/backend/src/main/java/com/codingx/chat/**
  - CodingX/frontend/src/views/ChatView.tsx
  - CodingX/docs/project/plans/phase-3-web-workbench/**
related_docs:
  - CodingX/docs/superpowers/memory/contract-chat-stream-and-persistence.md
  - CodingX/docs/project/plans/phase-3-web-workbench/topic-runtime-sse-ai-chat.md
entrypoints:
  - backend/src/main/java/com/codingx/chat/interfaces/controller/ChatController.java
  - backend/src/main/java/com/codingx/chat/interfaces/controller/ChatStreamController.java
  - backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java
  - frontend/src/views/ChatView.tsx
last_verified_commit: d33f3fa
status: active
---

# 职责

- 对外提供统一的聊天入口，而不是把聊天、搜索、产物输出拆成多个互相割裂的前端流程。
- 负责将用户请求路由到意图分类、联网搜索、模型生成、文档产物生成等子流程。
- 负责维护会话、消息、摘要、反馈、引用来源、产物等聊天工作台核心状态。
- 负责向前端持续发送流式事件，支持首包探测、取消任务、排队等待与最终完成收口。

# 入口点

- 后端入口当前位于 `ChatController` 与 `ChatStreamController`，但现状仍是“先发消息、再单独订阅会话流”的双接口骨架。
- 当前业务编排集中在 `ChatApplicationService`，已具备用户消息落库、模型流式读取、SSE 推送和助手消息收口的基本闭环。
- `backend/src/main/java/com/codingx/chat/application/service` 现在按 `admin/chat/conversation/search/support` 物理分组，保持原有 package 声明不变，目的是降低目录噪音而不是做跨包重构。
- 当前前端入口仅有静态页面 `frontend/src/views/ChatView.tsx`，尚未接入真实会话列表、消息流、右侧产物与引用来源面板。

# 不变量

- 聊天主链路必须先保证可用的实时流式体验，再叠加搜索、引用和产物能力。
- 第一阶段只做“在线搜索现搜现用”，不引入长期知识库、向量检索和 RAG 存储模型。
- 意图层需要做成类似 ragent 的独立能力层，但只覆盖本期需要的聊天、搜索、引用整理和文档产物输出。
- 深度思考能力由后端配置控制，默认开启，不在前端先暴露显式开关。

# 扩展点

- 模型层可继续从单一提供商扩展为多模型路由、fallback 和能力探测。
- 搜索层可从单一搜索提供方扩展为多搜索源与页面抓取器。
- 产物层可从单一 `docx` 输出扩展为 markdown、pdf 或任务附件产物。
- 意图层可继续扩展为更完整的树形意图体系与后台管理能力。

# 常见陷阱

- 不要把在线搜索现搜现用错误地建模成 RAG 知识库，否则会把第一期复杂度推高到不可控。
- 不要把聊天主链路依赖在前端临时状态上，所有消息、摘要、引用与产物都需要后端可恢复。
- 不要直接搬入 ragent 的知识库、检索、MCP 混合编排代码；当前只需要复用聊天、意图、流式、摘要、反馈、排队与路由模式。
