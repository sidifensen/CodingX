---
type: contract
title: admin-workspace-management-contract
summary: 管理端工作空间列表 API 与页面字段契约
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
last_verified_commit: 17224e3a
status: active
---

# Admin Workspace Management Contract

## Scope

本契约覆盖管理端工作空间只读列表能力。目标是让管理员快速定位工作空间归属、类型和最近活跃上下文，不覆盖用户侧聊天页的空间切换、目录绑定或默认云端空间创建流程。

## Producers And Consumers

- Producer: 后端管理端工作空间控制器，返回统一 `ApiResponse<PageResult<...>>`。
- Producer: 工作空间查询服务，从 `workspace` 读取有效记录，并按 `chat_conversation.workspace_id` 聚合会话数量。
- Consumer: `AdminChatApi.listWorkspaces`，负责拼接查询参数、携带 `satoken`、解析 `ApiResponse.message`。
- Consumer: `WorkspacePage`，负责筛选输入、分页展示、加载/错误/空态与深浅色主题展示。

## Interface Rules

- 列表路径：`GET /api/admin/workspaces`
- 查询参数：
  - `current`：页码，从 1 开始，后端小于 1 时归一为 1。
  - `size`：每页数量，后端限制在 1 到 100。
  - `keyword`：可选，匹配工作空间名称、仓库地址、分支、本地目录或数字 ID。
  - `runtimeTarget`：可选，支持 `ALL`、`cloud`、`local`，大小写不敏感。
- 返回分页结构沿用 `PageResult`：`records`、`total`、`size`、`current`、`pages`。
- 列表项至少包含：`id`、`name`、`repositoryUrl`、`branchName`、`workingDirectory`、`runtimeTarget`、`runtimeTargetLabel`、`createdBy`、`conversationCount`、`createdAt`、`updatedAt`。

## Invariants

- 后端只返回 `deleted = 0` 的工作空间。
- `runtimeTargetLabel` 必须由后端统一翻译：`cloud` 为 `云端`，`local` 为 `本地`，未知值为 `未知`。
- `conversationCount` 只统计未删除会话，工作空间没有会话时返回 `0`。
- 前端筛选和刷新必须复用同一个 `AdminChatApi.listWorkspaces` 入口，页面不得重复手写 `fetch` 和 `response.json()`。

## Compatibility Notes

- 历史环境可能存在旧的 `workspace_type` 语义，管理端新功能不得依赖该字段。
- 当前实现不要求新增表或字段，因此不需要迁移脚本；若未来扩展写能力触及表结构，必须同步更新迁移与 `schema.sql` 注释。
