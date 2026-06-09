# 任务完成提醒已读状态

## 功能用途

聊天侧栏的任务完成提醒用于提示用户：某个会话的后台任务已经结束，但用户还没有打开会话查看结果。

## 核心流程

1. 新建会话默认 `task_completion_read = 1`，表示无需提醒。
2. 后台任务进入终态后，后端将所属会话 `task_completion_read` 更新为 `0`。
3. 会话列表接口返回 `taskCompletionRead`，前端优先使用该字段判断是否展示提醒圆点。
4. 用户点击提醒或进入会话后，前端调用已读接口，后端将 `task_completion_read` 更新为 `1`。
5. 本地运行会话和旧快照仍可使用 `seenTaskFinishedAtByConversationId` 作为兼容兜底，但不能覆盖后端权威字段。

## 关键文件

- `backend/src/main/java/com/codingx/chat/domain/model/ChatConversation.java`：维护任务完成提醒已读领域状态。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatStreamExecutionService.java`：后台任务终态后标记提醒未读。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatConversationApplicationService.java`：提供会话归属校验后的已读更新能力。
- `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatController.java`：暴露会话列表字段和已读接口。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：使用 `taskCompletionRead` 渲染提醒，并在打开会话后回写已读。
- `frontend/user/src/views/chat/chatApi.ts`：封装会话列表解析和已读接口请求。

## 数据结构

- `chat_conversation.task_completion_read`：`SMALLINT NOT NULL DEFAULT 1`
- `0`：最近任务完成提醒未读，需要展示提醒
- `1`：最近任务完成提醒已读或无需提醒

## 边界约束

- `chat_conversation.pinned` 只表示置顶排序，不能作为提醒状态。
- `chat_conversation.share_token` 只表示公开分享令牌，不能作为提醒状态。
- 任务终态写提醒未读失败时只记录日志，不得反向影响任务成功或失败状态。

## 验证方式

- 后端：`mvn compile`、`mvn test`
- 前端：`npm run build`、`npm run test:run`
