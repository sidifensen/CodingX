# 管理端工作空间会话详情设计

## 背景

管理端已经提供工作空间列表和关联会话数量，但管理员无法从某个工作空间直接进入查看其下具体会话。用户期望“点进去后看到里面有什么会话”，因此本轮补齐只读详情能力，不引入工作空间编辑、删除或迁移。

本轮继续采用 autonomous 模式执行，不等待额外确认。

## 推荐方案

推荐新增“工作空间详情页 + 按工作空间分页查询会话接口”。

备选方案一是在前端拿全量会话后按工作空间过滤，但现有会话列表响应不包含 `workspaceId`，且前端过滤会放大数据量。备选方案二是给工作空间补完整详情 CRUD，但会扩大写能力和审计范围。推荐方案只补齐当前可观测缺口，边界清晰，风险最低。

## 后端设计

- 在 `AdminWorkspaceController` 新增 `GET /api/admin/workspaces/{workspaceId}/conversations`。
- 在 `AdminWorkspaceService` 新增 `pageWorkspaceConversations`，先调用 `WorkspaceRepository.ensureExists(workspaceId)`，再读取该空间未删除会话。
- 在 `ChatConversationRepository` 新增 `findAllByWorkspaceId(Long workspaceId, String keyword)`，筛选 `workspace_id = workspaceId`、`deleted = 0`，并沿用标题模糊/数字 ID 精确匹配。
- 响应复用 `PageResult<AdminChatConversationListItemResponse>`，字段与管理端会话列表保持一致，避免前端重复定义会话行结构。
- 不新增数据库字段，不修改迁移脚本。

## 前端设计

- `WorkspacePage` 中工作空间名称和 ID 变为 `Link`，目标为 `/workspaces/:workspaceId`，视觉上明确为可点击入口。
- `AdminChatApi` 新增 `listWorkspaceConversations(workspaceId, query)`，集中拼接分页与关键字参数，并复用统一 ApiResponse 解析。
- 新增 `WorkspaceDetailPage`，展示返回入口、工作空间 ID、会话总数、关键字搜索、刷新、分页、加载骨架、空态和错误提示。
- 会话行提供“查看详情”链接到现有 `/tasks/:conversationId`，便于继续查看消息明细。
- 页面只使用管理端主题令牌，继承浅色/暗色模式与滚动条主题，不使用浏览器原生弹窗。

## 测试策略

- 后端服务测试覆盖：校验工作空间存在、按工作空间查询会话、状态中文标签和分页结构。
- 后端控制器测试覆盖：`/api/admin/workspaces/{id}/conversations` 参数透传和 `ApiResponse` 分页结构。
- 仓储测试覆盖：新增按工作空间查询方法能返回绑定空间的会话领域对象。
- 前端 API 测试覆盖：按工作空间查询接口路径、分页参数和关键字参数。
- 前端页面测试覆盖：列表页链接、详情页会话渲染、搜索参数、骨架、空态和错误态。
- 完成后按前后端改动执行后端编译/测试、管理端构建/测试，并启动服务通过浏览器验证点击流。

## 自检

- 范围聚焦在只读详情和会话查看。
- 无数据库结构变更。
- 无原生弹窗。
- 沿用现有管理端会话响应，避免重复业务语义。
