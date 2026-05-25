---
type: contract
title: admin-workspace-management-contract
summary: 管理端工作空间列表与空间内会话查看 API/页面字段契约
tags:
  - admin
  - workspace
owned_paths:
  - backend/src/main/java/com/codingx/admin/interfaces/controller
  - backend/src/main/java/com/codingx/admin/application/service
  - backend/src/main/java/com/codingx/workspace
  - frontend/admin/src/api/adminChatApi.ts
  - frontend/admin/src/pages
related_docs:
  - docs/superpowers/memory/admin/workspace-management-module-card.md
entrypoints:
  - backend/src/main/java/com/codingx/chat/interfaces/response/PageResult.java
  - frontend/admin/src/components/DataTableCard.tsx
last_verified_commit: 2ddd1c5d
status: active
---

# Admin Workspace Management Contract

## Scope

本契约覆盖管理端工作空间只读列表能力，以及从工作空间进入查看空间内会话的详情能力。目标是让管理员快速定位工作空间归属、类型、最近活跃上下文和具体会话，不覆盖用户侧聊天页的空间切换、目录绑定或默认云端空间创建流程。

## Producers And Consumers

- Producer: 后端管理端工作空间控制器，返回统一 `ApiResponse<PageResult<...>>`。
- Producer: 工作空间查询服务，从 `workspace` 读取有效记录，并按 `chat_conversation.workspace_id` 聚合会话数量。
- Producer: 工作空间会话子资源，先校验 `workspace` 有效，再按 `chat_conversation.workspace_id` 查询未删除会话。
- Consumer: `AdminChatApi.listWorkspaces`，负责拼接查询参数、携带 `satoken`、解析 `ApiResponse.message`。
- Consumer: `AdminChatApi.listWorkspaceConversations`，负责查询指定空间内会话，并保留路由中的雪花 ID 字符串。
- Consumer: `WorkspacePage`，负责筛选输入、分页展示、加载/错误/空态与深浅色主题展示。
- Consumer: `WorkspaceDetailPage`，负责展示空间内会话分页、关键字查询、空态、错误态和会话详情跳转。

## Interface Rules

- 列表路径：`GET /api/admin/workspaces`
- 查询参数：
  - `current`：页码，从 1 开始，后端小于 1 时归一为 1。
  - `size`：每页数量，后端限制在 1 到 100。
  - `keyword`：可选，匹配工作空间名称、仓库地址、分支、本地目录或数字 ID。
  - `runtimeTarget`：可选，支持 `ALL`、`cloud`、`local`，大小写不敏感。
- 返回分页结构沿用 `PageResult`：`records`、`total`、`size`、`current`、`pages`。
- 列表项至少包含：`id`、`name`、`repositoryUrl`、`branchName`、`workingDirectory`、`runtimeTarget`、`runtimeTargetLabel`、`createdBy`、`conversationCount`、`createdAt`、`updatedAt`。
- 空间会话路径：`GET /api/admin/workspaces/{workspaceId}/conversations`
- 空间会话查询参数：
  - `current`：页码，从 1 开始，后端小于 1 时归一为 1。
  - `size`：每页数量，后端限制在 1 到 100。
  - `keyword`：可选，匹配会话标题或数字会话 ID。
- 空间会话返回分页结构复用管理端会话列表项：`id`、`title`、`createdBy`、`status`、`statusLabel`、`lastMessageAt`、`lastRunId`、`createdAt`、`updatedAt`。

## Invariants

- 后端只返回 `deleted = 0` 的工作空间。
- `runtimeTargetLabel` 必须由后端统一翻译：`cloud` 为 `云端`，`local` 为 `本地`，未知值为 `未知`。
- `conversationCount` 只统计未删除会话，工作空间没有会话时返回 `0`。
- 前端筛选和刷新必须复用同一个 `AdminChatApi.listWorkspaces` 入口，页面不得重复手写 `fetch` 和 `response.json()`。
- 工作空间详情页请求会话前必须保留 `workspaceId` 的字符串形式，禁止转成 JavaScript `Number`，否则雪花 ID 会发生精度丢失并导致后端误判“工作空间不存在”。
- 空间会话查询必须先调用工作空间存在性校验，缺失空间通过全局异常处理返回统一中文错误。

## Compatibility Notes

- 历史环境可能存在旧的 `workspace_type` 语义，管理端新功能不得依赖该字段。
- 当前实现不要求新增表或字段，因此不需要迁移脚本；若未来扩展写能力触及表结构，必须同步更新迁移与 `schema.sql` 注释。
- 管理端路由和 API 层面对所有数据库长整型 ID 应按字符串传递；小 ID 测试不能覆盖雪花 ID 精度风险，需保留大 ID 回归测试。
