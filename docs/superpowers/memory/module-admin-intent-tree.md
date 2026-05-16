---
type: module_card
title: admin-intent-tree
summary: CodingX 管理端意图树配置模块，覆盖后台 CRUD、树形接口、节点扩展字段与前端配置台。
tags:
  - chat
  - admin
  - intent
owned_paths:
  - CodingX/backend/src/main/java/com/codingx/chat/**/AdminChatIntent*
  - CodingX/backend/src/main/java/com/codingx/chat/domain/model/ChatIntentNode.java
  - CodingX/backend/src/main/java/com/codingx/chat/domain/repository/ChatIntentNodeRepository.java
  - CodingX/backend/src/main/java/com/codingx/chat/infrastructure/persistence/**/ChatIntentNode*
  - CodingX/backend/src/main/resources/db/schema.sql
  - CodingX/backend/src/main/resources/db/init.sql
  - CodingX/backend/src/main/resources/db/migration/*intent*
  - CodingX/frontend/admin/src/pages/IntentTreePage.tsx
  - CodingX/frontend/admin/src/api/adminChatApi.ts
status: active
---

# 管理端意图树模块

## 模块边界

管理端意图树用于维护聊天运行时的意图层级、节点类型和 MCP 绑定。前端负责树形展示、节点详情和弹窗编辑；后端负责节点持久化、树形聚合、字段校验、逻辑删除和兼容聊天运行时已有的 `intentType` 分流。

## 稳定契约

- API 基路径为 `/api/admin/chat/intents`，现有列表和保存接口需要继续可用。
- 树形配置台需要新增 `/tree`、`/{id}` 更新和删除接口，响应仍包裹在 `ApiResponse` 中。
- `intentType` 是聊天运行时的分流字段，取值继续使用 `kb`、`system`、`mcp`；新增的 `kind` 负责管理端与 ragent 风格配置，`0=KB`、`1=SYSTEM`、`2=MCP`。
- 示例问题会在节点扩展字段 `examples` 中以 JSON 数组字符串保存，现有 `chat_intent_example` 表仍用于当前聊天意图识别链路。
- 所有数据库结构变更必须同时更新迁移脚本、`schema.sql` 和初始化数据。

## 当前缺口

- 当前页面还是表单加表格，缺少 ragent 风格的左树右详情布局、弹窗编辑和字段分组。
- 当前后端节点模型缺少 `level`、`kind`、`examples`、`collectionName`、`topK`、`promptSnippet`、`sortOrder` 等管理字段。
- 当前管理接口缺少树形返回、按 ID 更新、按 ID 逻辑删除和重复编码校验。
