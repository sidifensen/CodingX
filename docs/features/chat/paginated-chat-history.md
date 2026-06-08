# 聊天历史分页加载

## 功能用途

聊天页会话列表和消息历史改为 cursor 分页加载，首屏只读取最近一页，用户需要更多历史时再显式加载下一页，避免会话数或消息数较大时把全量数据一次性传给前端。

## 使用入口

- 用户前端聊天页侧栏首次进入当前工作空间时，自动加载最近一页会话。
- 侧栏工作空间分组底部出现“加载更多会话”时，点击后继续读取下一页。
- 打开历史会话时，消息区默认读取最近一页消息；顶部出现“加载更早消息”时，点击后读取更旧消息。

## 核心流程

1. 前端 `useChatWorkspace` 在初始化或切换工作空间时调用 `ChatApi.listConversationPage`，传入 `workspaceId`、`pageSize` 和可选 cursor。后端 `ChatController` 识别分页参数后返回 `CursorPageResponse`，没有分页参数时继续返回旧数组，兼容既有调用方。
2. `ChatConversationApplicationService` 校验工作空间归属并归一化分页大小，再调用会话仓储按置顶、最近更新时间和会话 ID 排序读取一页数据。仓储多查一条用于判断 `hasMore`，并从最后一条有效记录生成下一页 cursor。
3. 侧栏点击加载更多时，`loadMoreConversations` 读取目标工作空间分区的 cursor，合并远端返回的会话到本地快照，并按会话 ID 去重。若请求失败，分页加载态会恢复，前端优先展示后端 `ApiResponse.message` 或统一错误文案。
4. 用户打开会话时，`selectConversation` 调用 `ChatApi.listMessagePage` 获取最近消息页，同时并行读取步骤、引用、产物和当前工具上下文。消息分页状态记录当前会话 ID、是否还有更早消息和最旧消息 cursor，避免切换会话后旧请求误写。
5. 消息区点击“加载更早消息”后，`loadOlderMessages` 带上 `beforeCreatedAt` 与 `beforeId` 查询旧消息页，并把返回记录前置合并到当前消息列表。合并时按消息 ID 去重，成功后持久化当前会话回放快照，失败时保留已有消息。

## 关键文件

- `backend/src/main/java/com/codingx/chat/interfaces/response/CursorPageResponse.java`：统一分页响应结构。
- `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatController.java`：分页参数协议适配和旧接口兼容。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatConversationApplicationService.java`：会话与消息分页编排、归属校验和 cursor 生成。
- `frontend/user/src/views/chat/chatApi.ts`：前端分页 API、cursor 参数映射和流式请求统一入口。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：会话/消息分页状态、加载更多动作和本地快照合并。
- `frontend/user/src/components/sidebar/ConversationHistory.tsx`、`frontend/user/src/views/chat/ChatMessageList.tsx`：侧栏会话历史和消息列表拆分后的展示组件。

## 关键数据结构

- `CursorPageResponse<T>`：包含 `items`、`hasMore`、`nextCursor`。
- `CursorPageCursor`：会话分页使用 `cursorPinned`、`cursorUpdatedAt`、`cursorId`；消息分页使用 `cursorCreatedAt`、`cursorId`。
- `ConversationPaginationState`：记录每个工作空间分区的下一页 cursor 和加载态。
- `MessagePaginationState`：记录当前会话是否还有更早消息、最旧消息 cursor 和加载态。

## 验证方式

- 后端分页单测覆盖会话分页、消息分页和旧数组响应兼容。
- 前端 API 与 hook 单测覆盖分页参数、首屏加载、加载更多会话和加载更早消息。
- 用户前端构建和测试用于验证类型、页面集成和组件拆分后的行为。
