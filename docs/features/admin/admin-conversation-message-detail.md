# 管理端会话消息详情

## 功能用途

管理端会话详情页用于排查单条会话内每条消息的完整运行信息，帮助管理员核对消息正文、模型路由、深度思考、附件和持久化状态。

## 使用入口

- 管理端菜单“会话管理”。
- 会话列表点击某条会话后进入 `/tasks/:id`。
- 后端接口：`GET /api/admin/chat/conversations/{conversationId}`。

## 核心流程

1. 管理员进入会话详情页后，`TaskDetail` 读取路由中的会话 ID 并调用 `AdminChatApi.getConversationDetail`；接口失败时沿用统一错误提取逻辑展示后端中文 `message`。
2. 后端 `AdminChatConversationService.getConversationDetail` 先读取会话元信息，再按会话 ID 查询消息列表；每条消息通过 `toMessageResponse` 补齐 `runId`、`deleted`、`createdAt`、`updatedAt`、模型元数据、思考内容和附件。
3. 前端 `MessageItem` 将轻量字段放入“消息元信息”表格，将 `content`、`thinkingContent` 和附件明细分区展示；空字段统一显示 `-`，空附件显示“无附件”。
4. 附件明细按附件响应字段逐项展示，包括附件 ID、会话 ID、消息 ID、类型、文件名、扩展名、MIME、文件大小、预览地址、内容摘要、状态和创建时间。

## 关键文件

- `backend/src/main/java/com/codingx/chat/interfaces/response/ChatMessageResponse.java`：消息响应字段定义。
- `backend/src/main/java/com/codingx/admin/application/service/AdminChatConversationService.java`：管理端会话详情聚合。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatConversationViewService.java`：用户端与分享页消息响应投影。
- `frontend/admin/src/pages/TaskDetail.tsx`：管理端会话详情页消息展示。
- `frontend/admin/src/api/adminChatApi.ts`：管理端消息响应类型。

## 验证方式

- 后端：`cd backend && mvn -Dtest=AdminChatConversationServiceTest test`。
- 前端：`cd frontend/admin && npm run build`。
- 浏览器：通过 CDP 打开 `http://localhost:5003/tasks/{conversationId}`，确认消息元信息、正文、深度思考内容和附件明细可见。
