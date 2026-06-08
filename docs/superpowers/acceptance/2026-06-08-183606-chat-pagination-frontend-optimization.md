# Acceptance Criteria: Chat Pagination and Frontend Optimization

**Spec:** `docs/superpowers/specs/2026-06-08-183606-chat-pagination-frontend-optimization-design.md`
**Date:** 2026-06-08
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 会话列表分页接口在传入 `pageSize` 时只返回第一页数据和下一页 cursor。 | API | 当前用户存在超过 `pageSize` 条会话。 | 响应 `data.items.length <= pageSize`，`data.hasMore=true`，`data.nextCursor.cursorId` 非空。 |
| AC-002 | 会话列表旧接口调用不带分页参数时保持旧版数组响应。 | API | 当前用户存在至少一条会话。 | 响应 `data` 是数组，旧前端调用不需要读取 `items` 字段。 |
| AC-003 | 显式工作空间会话分页必须校验用户归属。 | API | 请求用户访问不属于自己的 `workspaceId`。 | 后端通过统一异常返回中文错误，HTTP 状态与既有越权语义一致。 |
| AC-004 | 消息分页接口首次加载只返回最近一页消息并按时间升序展示。 | API | 会话内存在超过 `pageSize` 条未删除消息。 | 响应 `data.items.length <= pageSize`，列表顺序为旧到新，`hasMore=true`。 |
| AC-005 | 消息分页接口使用上一页 cursor 能加载更旧消息且不重复。 | API | 已取得最近页的最早消息 cursor。 | 下一页所有消息都早于 cursor，且不包含上一页消息 ID。 |
| AC-006 | 前端 `ChatApi` 正确解析会话分页响应。 | Logic | Mock fetch 返回分页 envelope。 | `listConversationPage` 返回字符串化 ID、`items`、`hasMore` 和 `nextCursor`。 |
| AC-007 | 前端 `ChatApi` 正确解析消息分页响应。 | Logic | Mock fetch 返回消息分页 envelope。 | `listMessagePage` 返回标准化附件/技能字段和 `hasMoreBefore` 语义。 |
| AC-008 | 首屏 bootstrap 不再请求全量会话列表。 | Logic | `useChatWorkspace` 初始化且用户已登录。 | 第一次会话请求 URL 包含分页参数，状态只保存第一页会话。 |
| AC-009 | Sidebar 点击“查看更多”触发真实下一页请求。 | UI interaction | 当前分区 `hasMore=true`。 | 点击后发起带 cursor 的分页请求，追加新会话，已有会话不重复。 |
| AC-010 | 选择会话时只加载最近一页消息。 | Logic | 会话内存在多页消息。 | 选择会话后消息区只包含最近页，`hasMoreBefore=true`。 |
| AC-011 | ChatView 顶部加载旧消息时不清空当前消息。 | UI interaction | 当前会话已加载最近页且还有旧消息。 | 触发加载旧消息后，旧消息 prepend 到现有消息前，当前消息仍保留且无重复 ID。 |
| AC-012 | 流式聊天请求构造不再直接散落在 `useChatWorkspace`。 | Logic | 检查前端源码。 | `useChatWorkspace` 调用 API 层流式请求方法，流式 URL/headers/signal 构造集中在 `chatApi` 或相邻 API 模块。 |
| AC-013 | Sidebar 拆分后保留现有关键操作入口。 | UI interaction | 登录后打开用户端聊天页。 | 新建会话、选择会话、置顶、分享、导出、删除、批量操作按钮仍可见并调用原回调。 |
| AC-014 | ChatView 消息列表拆分后保持空态和消息态一致。 | UI interaction | 分别进入无消息会话和有消息会话。 | 无消息会话显示原空态，有消息会话渲染用户/助手消息和过程卡片。 |
| AC-015 | `.gitignore` 不再忽略新建 `docs/` 文档。 | Logic | 执行 `git check-ignore docs/features/example.md`。 | 命令返回非 0，说明 docs 下新文档不会被忽略。 |
| AC-016 | 用户端和管理端 package 名称明确且 clean 脚本跨平台。 | Logic | 读取两个 `package.json`。 | `name` 不等于 `react-example`，`clean` 不包含 `rm -rf`。 |
| AC-017 | 新增分页测试不继续堆入既有巨型测试文件。 | Logic | 检查新增测试文件。 | 分页相关前端测试位于新的专用测试文件中。 |
