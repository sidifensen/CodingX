---
type: module_card
title: admin-workspace-management
summary: 管理端工作空间管理用于只读排查 workspace 归属、运行目标与会话数量
tags:
  - admin
  - workspace
owned_paths:
  - backend/src/main/java/com/codingx/admin
  - backend/src/main/java/com/codingx/workspace
  - frontend/admin/src/pages
  - frontend/admin/src/api/adminChatApi.ts
entrypoints:
  - backend/src/main/java/com/codingx/workspace/infrastructure/persistence/dataobject/WorkspaceDO.java
  - backend/src/main/java/com/codingx/workspace/infrastructure/repository/WorkspaceRepositoryImpl.java
  - frontend/admin/src/components/Layout.tsx
  - frontend/admin/src/App.tsx
last_verified_commit: 17224e3a
status: active
---

# Admin Workspace Management Module Card

## Responsibilities

- 管理端工作空间管理面向运营和排查场景，展示 `workspace` 表中的有效记录、创建人、运行目标、仓库/本地目录上下文和会话数量。
- 后端应保持只读查询边界，不在管理端工作空间页隐式创建默认云端空间，也不改变用户侧工作空间归属。
- 前端页面应复用管理端 `Layout`、`DataTableCard`、分页组件、认证事件和主题令牌，避免页面内另写接口解析或硬编码单主题颜色。

## Entry Points

- `WorkspaceDO` 映射 `workspace` 表字段，`runtime_target` 只支持 `cloud` 和 `local`。
- `WorkspaceRepositoryImpl` 负责用户侧存在性校验、归属校验和默认云端空间补齐。
- 管理端控制器统一放在 `com.codingx.admin.interfaces.controller`，路径使用 `/api/admin/...`。
- 管理端前端路由由 `frontend/admin/src/App.tsx` 承载，侧边栏由 `frontend/admin/src/components/Layout.tsx` 承载。

## Invariants

- `workspace.deleted = 0` 才能进入管理端列表和统计。
- `runtime_target = cloud` 表示云端历史空间，`runtime_target = local` 表示本地目录空间。
- 工作空间列表不能混入用户侧的默认空间创建副作用，否则打开管理端页面会改变业务数据。
- 管理端接口错误必须继续返回统一 `ApiResponse`，前端只消费 `AdminChatApi` 集中封装后的异常文案。

## Extension Points

- 后续如需删除、重命名、归档或迁移工作空间，应先明确业务规则并补充写接口、审计和权限测试。
- 如需更高性能的会话数量统计，可将当前按工作空间聚合逻辑下沉为数据库聚合查询或 Mapper XML。
- 如需展示用户名称，可通过 `UserRepository` 按 `created_by` 补齐，避免前端二次请求用户列表。

## Common Pitfalls

- 不要把 `workspace_type` 旧字段重新写回数据库；当前业务语义已经统一为 `runtime_target`。
- 不要在管理端工作空间列表中调用 `ensureDefaultCloudWorkspace`，它会产生写入副作用。
- 不要用浏览器原生弹窗做管理端交互；新增交互必须使用项目内自定义浮层。
- 不要只验证浅色主题；管理端页面必须在 `.dark` 下仍可读，滚动容器应复用全局滚动条令牌。
