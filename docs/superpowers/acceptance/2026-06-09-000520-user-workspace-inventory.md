# Acceptance Criteria: User Workspace Inventory

**Spec:** `docs/superpowers/specs/2026-06-09-000520-user-workspace-inventory-design.md`
**Date:** 2026-06-09
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 用户侧工作区接口只返回当前用户未删除工作区。 | API | 当前用户 ID 为 `1001`，数据库查询包含当前用户、本地空工作区、其他用户工作区和已删除工作区。 | `/api/chat/workspaces` 响应只包含当前用户且 `deleted = 0` 的工作区。 |
| AC-002 | 用户侧工作区响应保持 Long ID 字符串安全。 | API | 工作区 ID 为雪花长整型，前端请求 `ChatApi.listWorkspaces`。 | 前端收到的 `id` 字段为字符串，不被转换成 JavaScript Number。 |
| AC-003 | 服务端返回的空本地工作区会显示在用户侧工作区分组中。 | Logic | 前端 localStorage 没有该目录快照，`/api/chat/workspaces` 返回 `D:/code/CodingX`。 | `useChatWorkspace` 的 `workspaceGroups` 包含 `CodingX` 分组，`workspacePath` 为 `D:/code/CodingX`，`conversations` 为空。 |
| AC-004 | 已有本地快照分组不会被服务端库存覆盖会话。 | Logic | localStorage 中 `D:/code/CodingX` 已有会话，服务端也返回同一路径工作区。 | 合并后的分组仍保留原会话、激活会话和本地分区状态。 |
| AC-005 | 空本地工作区点击切换仍沿用目录绑定和会话分页。 | Logic | 侧栏分组来自服务端库存且没有会话。 | 调用 `setActiveWorkspacePath('D:/code/CodingX')` 后先调用 `bindWorkspacePath`，再用返回的 workspaceId 请求 `/api/chat/conversations?workspaceId=...`。 |
