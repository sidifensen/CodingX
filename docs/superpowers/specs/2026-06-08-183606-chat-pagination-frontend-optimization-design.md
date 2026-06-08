# Chat Pagination and Frontend Optimization Design

**Date:** 2026-06-08
**Status:** Approved

## Scope

本次优化聚焦用户端聊天工作区的加载方式与前端结构治理。会话列表和消息列表必须从“接口一次返回全量、前端再裁剪展示”改为“后端分页返回、前端按需追加或前置加载”。同时处理已确认的低风险治理项：取消 `.gitignore` 对 `docs/` 的忽略、修正用户端/管理端 package 元信息与跨平台 clean 脚本、把聊天流式请求从 `useChatWorkspace` 中收敛到 API 层、拆分 Sidebar/App/ChatView 周边职责。

本次不做 CI 配置，也不抽取 user/admin 共享前端包。现有未提交改动视为其他会话成果，不纳入本次修改范围。

## Goals

1. 会话列表首屏只加载固定页大小的数据，点击或滚动加载更多时才请求下一页。
2. 会话消息首屏只加载最近一页，用户向上查看历史时再加载更早消息。
3. Sidebar 不再用已全量加载的数组做本地分页，加载更多必须触发真实接口请求。
4. ChatView 不再直接承担完整消息列表渲染与历史加载入口，消息列表相关 UI 拆出边界清晰的组件。
5. `useChatWorkspace` 中的流式 fetch 构造与请求细节迁移到聊天 API 层，Hook 只编排状态。
6. `.gitignore` 不再忽略 `docs/`，避免新功能文档和 superpowers 文档漏交。
7. 用户端和管理端 package 名称不再是 `react-example`，clean 脚本可在 Windows 环境执行。

## Backend Design

会话分页接口沿用 `/api/chat/conversations`，新增 `pageSize`、`cursorUpdatedAt`、`cursorId` 参数。未传分页参数时保持旧版 `List<ChatConversationResponse>` 响应，保证旧调用兼容；传入分页参数时返回 cursor 分页响应，包含 `items`、`hasMore` 和 `nextCursor`。排序规则保持 `pinned desc, updated_at desc, id desc`，cursor 使用 `updatedAt + id` 表示下一页起点。

消息分页接口沿用 `/api/chat/conversations/{conversationId}/messages`，新增 `pageSize`、`beforeCreatedAt`、`beforeId` 参数。未传分页参数时保持旧版 `List<ChatMessageResponse>` 响应；传入分页参数时返回最近一页或指定游标之前的消息页。数据库查询先按 `created_at desc, id desc` 取一页加一条判断 `hasMore`，响应前再恢复为升序，保证前端渲染顺序稳定。

应用服务继续负责会话归属校验。显式工作空间分页时必须先校验工作空间归属；默认云端历史分页需合并默认云端工作空间与旧未归属会话，保持现有可见范围。为降低复杂度，默认云端历史合并分页可在应用层合并两个来源后裁剪，但仓储层必须提供工作空间内的 cursor 分页能力，避免本地/显式工作空间读取全量。

## Frontend Design

`ChatApi` 增加 `listConversationPage` 和 `listMessagePage`，旧的 `listConversations`、`listMessages` 可作为兼容包装。分页响应类型在 `types.ts` 中定义，前端统一使用字符串 ID，cursor 字段不暴露 Long 精度风险。

`useChatWorkspace` 为当前工作区分区维护会话分页状态：已加载 `items`、`hasMore`、`nextCursor`、`isLoadingMore`。首屏 bootstrap 只加载第一页；Sidebar 的“查看更多”调用 `loadMoreConversations`，加载成功后把新页合并进当前分区快照。已有置顶、任务完成提醒、本地快照兼容逻辑继续作用于已加载集合，不再假设当前集合就是全部远端数据。

消息状态增加 `messagePagination`，包括 `hasMoreBefore`、`oldestCursor`、`isLoadingOlder`。选择会话或刷新恢复时只加载最近一页；ChatView 顶部触达时调用 `loadOlderMessages`，将旧消息 prepend 到现有消息前，并由 UI 保持滚动相对位置。发送、流式生成、取消、重试等实时路径仍只更新当前已加载消息集合。

## Component Boundaries

Sidebar 拆成导航壳层、会话历史区、分组区、单行会话、菜单/批量操作等子组件，保留原有视觉和暗色主题表现。拆分目标是降低单文件复杂度，不改变用户交互语义。

App 拆出轻量路由/主题 Hook 或壳层组件，保持 `MainApp` 只负责组合认证、宿主上下文、聊天工作区和页面布局。ChatView 拆出 `ChatMessageList`，由它承接消息列表渲染、顶部加载更多入口和空态之间的边界。

## Error Handling

分页接口仍通过全局异常处理器返回 `ApiResponse`。前端 API 层继续优先透出后端 `ApiResponse.message`，分页加载失败时仅展示加载失败状态，不清空已有列表或消息。重复点击加载更多时通过 `isLoadingMore` / `isLoadingOlder` 防抖。

## Testing

后端增加会话分页和消息分页单元/控制器测试，覆盖首次页、下一页、归属校验和旧接口兼容。前端增加 `chatApi` 分页解析测试、`useChatWorkspace` 首屏分页和加载更多测试、ChatView 顶部加载旧消息测试。大型既有测试文件不做无关迁移，但新增分页测试放入独立文件，后续增量测试不再继续堆入巨型测试文件。
