# 聊天消息操作

## 功能用途

聊天消息操作用于支持会话内消息删除和编辑重发的上下文清理。删除采用 `chat_message.deleted` 软删除，不物理移除内容，保证前端刷新后隐藏已删消息，同时保留数据库审计线索。

## 使用入口

前端用户聊天页在消息操作区触发删除后，调用 `DELETE /api/chat/conversations/{conversationId}/messages`，请求体传入 `messageIds`。后端返回统一 `ApiResponse`，删除成功文案为“消息删除成功”。

## 核心流程

1. `ChatController.deleteMessages` 接收会话 ID 和消息 ID 列表；请求体为空时按空列表处理，避免协议层抛空指针。
2. `ChatConversationApplicationService.deleteConversationMessages` 先读取会话并校验 `createdBy` 等于当前登录用户；校验失败时抛出权限异常，不允许跨用户删除消息。
3. 服务层过滤空消息 ID 并去重；过滤后为空则直接返回，不刷新会话时间，也不生成无条件更新。
4. `ChatMessageRepositoryImpl.softDeleteByConversationIdAndIds` 只在当前 `conversation_id` 和指定 `id` 范围内更新未删除消息，将 `deleted` 置为 `1` 并刷新 `updated_at`。
5. `findByConversationId` 固定过滤 `deleted = 0` 并按 `created_at` 升序返回，因此删除后的消息不会再进入前端回放、模型历史或会话上下文。

## 关键文件

- `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatController.java`：消息删除接口。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatConversationApplicationService.java`：会话归属校验、消息 ID 过滤和删除编排。
- `backend/src/main/java/com/codingx/chat/infrastructure/persistence/repository/conversation/ChatMessageRepositoryImpl.java`：消息查询过滤和软删除 SQL 条件。
- `backend/src/test/java/com/codingx/chat/application/service/ChatConversationApplicationServiceTest.java`：删除权限与去重委托测试。
- `backend/src/test/java/com/codingx/chat/infrastructure/persistence/repository/ChatMessageRepositoryImplTest.java`：仓储映射和软删除更新测试。

## 关键逻辑

消息删除必须同时限制 `conversation_id`、`id in (...)` 和 `deleted = 0`，避免跨会话误删或重复刷新历史记录。仓储层使用 `UpdateWrapper` 显式 `set("deleted", 1)` 和 `set("updated_at", now)`；不能只把 `deleted=1` 放进实体对象后调用 `update(entity, wrapper)`，否则在 MyBatis-Plus 逻辑删除配置下可能出现接口返回成功但 `deleted` 没有进入 SET 子句的情况。

前端本地快照可能短暂保留旧消息；刷新后以后端 `/messages` 回放为准。若需要手动清理某次异常历史，应优先通过消息删除接口；只有接口链路本身异常时，才使用数据库上下文精确软删除指定消息。

## 测试与验证

- `mvn -Dtest=ChatConversationApplicationServiceTest test`
- `mvn -Dtest=ChatMessageRepositoryImplTest test`
- 浏览器 CDP 使用当前登录态调用 `DELETE /api/chat/conversations/{conversationId}/messages` 后，通过 PostgreSQL 查询确认目标消息 `deleted = 1`。
