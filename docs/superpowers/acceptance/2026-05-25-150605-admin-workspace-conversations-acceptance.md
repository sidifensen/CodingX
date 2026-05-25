# Acceptance Criteria: 管理端工作空间会话详情

**Spec:** `docs/superpowers/specs/2026-05-25-150605-admin-workspace-conversations-design.md`
**Date:** 2026-05-25
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 管理端可按工作空间 ID 分页查询该空间下未删除会话。 | API | 存在 ID 为 3001 的工作空间，且存在绑定 `workspace_id=3001` 的会话。 | `GET /api/admin/workspaces/3001/conversations?current=1&size=10` 返回 `success=true`，`data.records` 包含该空间会话，分页字段完整。 |
| AC-002 | 后端查询工作空间会话前必须校验工作空间存在。 | Logic | 调用服务方法 `pageWorkspaceConversations(3001, 1, 10, null)`。 | `WorkspaceRepository.ensureExists(3001)` 被调用，空间不存在时由全局异常处理返回中文错误。 |
| AC-003 | 工作空间会话接口支持关键字过滤。 | API | 工作空间 3001 下存在标题包含“报销”或 ID 命中的会话。 | 请求携带 `keyword=报销` 时，后端将关键字传入仓储并只返回匹配会话。 |
| AC-004 | 工作空间列表页的工作空间名称可点击进入详情页。 | UI interaction | 管理端工作空间列表返回 ID 为 3001、名称为“本地项目”的记录。 | 页面渲染链接“本地项目”，其 `href` 为 `/workspaces/3001`。 |
| AC-005 | 工作空间详情页展示该空间下会话并可进入会话详情。 | UI interaction | `AdminChatApi.listWorkspaceConversations(3001)` 返回一个 ID 为 2001 的会话。 | `/workspaces/3001` 展示该会话标题、状态、创建人，并提供 `/tasks/2001` 的查看详情链接。 |
| AC-006 | 工作空间详情页处理加载、空态和接口错误。 | UI interaction | API 分别处于 pending、返回空 records、抛出错误三种状态。 | 页面分别展示骨架行、“当前工作空间暂无会话”和后端错误文案。 |

## Coverage Check

- 后端接口、服务校验、仓储过滤、前端跳转、详情页渲染和异常态均有可执行验证。
- 无写操作与数据库结构变更验收项，因为设计已明确排除。
