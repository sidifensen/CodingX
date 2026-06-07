# Acceptance Criteria: User Memory Management

**Spec:** `docs/superpowers/specs/2026-06-07-181536-user-memory-management-design.md`
**Date:** 2026-06-07
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 用户端侧边栏提供“记忆管理”入口并同步 `/memories` 路由。 | UI interaction | 用户端应用已渲染。 | 点击“记忆管理”后主区显示“记忆管理”标题，地址栏路径为 `/memories`，会话查询参数被清理。 |
| AC-002 | 记忆管理页按当前工作空间查询用户可见记忆。 | Logic | 用户已登录且当前工作空间 ID 为 `3001`。 | 页面加载时调用 `/api/chat/memories?workspaceId=3001&status=ACTIVE` 或筛选对应状态接口，并展示用户记忆和当前项目记忆。 |
| AC-003 | 页面支持按范围和状态筛选。 | UI interaction | 列表中同时存在 USER、PROJECT、ACTIVE、REJECTED 记忆。 | 点击“项目记忆”只显示 PROJECT；点击“已停用”只显示 REJECTED；点击“全部”恢复对应全集。 |
| AC-004 | 用户可以编辑自己记忆的正文。 | API | 记忆 `9001` 属于当前用户，提交正文非空。 | `PATCH /api/chat/memories/9001` 返回更新后的内容，页面列表同步展示新正文。 |
| AC-005 | 用户不能提交空记忆正文。 | Logic | 编辑弹窗已打开。 | 保存空白正文不会发起请求，页面显示“记忆内容不能为空”。 |
| AC-006 | 用户可以停用和重新启用记忆。 | API | 记忆 `9001` 属于当前用户。 | 状态接口收到 `REJECTED` 或 `ACTIVE` 后返回对应状态，页面按钮文案和状态徽标同步更新。 |
| AC-007 | 用户可以删除自己的记忆。 | API | 记忆 `9001` 属于当前用户且确认删除弹窗已打开。 | `DELETE /api/chat/memories/9001` 成功后，该记忆从页面列表移除，后端逻辑删除字段置为 `1`。 |
| AC-008 | 未登录用户进入记忆管理页不会请求记忆接口。 | UI interaction | 本地没有认证 session。 | 页面显示登录提示；点击登录按钮打开项目内登录弹窗；未调用 `/api/chat/memories`。 |
| AC-009 | 后端编辑和删除接口必须校验记忆归属。 | Logic | 记忆归属用户不是当前登录用户。 | 服务抛出 `GOVERNANCE_MEMORY_FORBIDDEN`，全局异常处理返回中文 `ApiResponse.message`。 |
| AC-010 | 删除或停用后的记忆不参与模型上下文回注。 | Logic | 记忆状态为 REJECTED 或 deleted 为 1。 | `retrieveActiveMemories` 不返回该记忆。 |
| AC-011 | 记忆管理页面支持亮暗主题可读性。 | UI interaction | 用户端运行在暗色主题。 | 主容器背景非透明，文本、边框、筛选按钮和弹窗内容清晰可读。 |

