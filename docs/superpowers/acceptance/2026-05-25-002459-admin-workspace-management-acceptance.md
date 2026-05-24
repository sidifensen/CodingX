# Acceptance Criteria: 管理端工作空间管理

**Spec:** `docs/superpowers/specs/2026-05-25-002459-admin-workspace-management-design.md`
**Date:** 2026-05-25
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 管理端工作空间 API 返回统一分页结构和工作空间字段 | API | 后端控制器单元测试中服务返回一条工作空间记录 | `GET /api/admin/workspaces?current=1&size=10` 返回 `success=true`，`data.records[0].name`、`runtimeTargetLabel`、`conversationCount` 与服务结果一致 |
| AC-002 | 工作空间列表支持运行目标筛选参数 | API | 调用 `/api/admin/workspaces?runtimeTarget=local` | 控制器向服务透传 `local`，响应仍为 `ApiResponse<PageResult>` |
| AC-003 | 工作空间服务只读分页并正确生成运行目标标签 | Logic | 仓储返回 cloud 与 local 工作空间记录及会话数量 | 服务返回 `云端`、`本地` 标签，分页 `total/current/pages` 正确 |
| AC-004 | 前端 API 使用统一管理端请求入口查询工作空间 | Logic | Mock `fetch` 返回工作空间分页包裹数据 | `AdminChatApi.listWorkspaces({ current: 2, size: 10, keyword: "repo", runtimeTarget: "local" })` 请求 URL 包含对应查询参数并解析 `records` |
| AC-005 | 管理端侧边栏提供工作空间入口并路由到页面 | UI interaction | 渲染管理端应用或布局 | 页面中存在“工作空间”导航项，访问 `/workspaces` 时渲染工作空间管理标题 |
| AC-006 | 工作空间页面展示列表、统计、筛选和分页摘要 | UI interaction | Mock `AdminChatApi.listWorkspaces` 返回一条本地工作空间 | 页面显示工作空间名称、`本地` 标签、会话数、仓库或目录字段，并显示 `第 1 / 1 页，共 1 条` |
| AC-007 | 工作空间页面加载态、空态、错误态可观测 | UI interaction | API 分别处于 pending、返回空列表、抛出错误 | pending 时展示 10 行骨架；空列表时显示 `暂无工作空间`；错误时显示后端错误文案或兜底中文文案 |
| AC-008 | 工作空间页面不使用浏览器原生弹窗且保持深浅色令牌 | UI interaction | 静态检查页面实现 | 页面代码不调用 `window.alert`、`window.confirm`、`window.prompt`，样式类使用项目主题令牌而非单主题硬编码颜色 |
