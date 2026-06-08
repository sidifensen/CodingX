# 聊天桌面目标模式

## 功能用途

桌面端聊天工作台的“目标模式”用于让模型显式创建和跟进会话级目标。目标状态以后端数据库为权威来源，由 `get_goal`、`create_goal`、`update_goal` 工具读写，不再把普通聊天过程里的 `executionSteps` 推导成目标进度。

## 使用入口

用户在聊天输入区开启“目标模式”后，前端继续把本轮请求标记为 `planMode=true`。后端系统提示会让模型在需要长期跟进、多步骤开发、调试修复或用户明确要求目标管理时调用目标工具。页面刷新或切换会话时，应通过 `GET /api/chat/conversations/{conversationId}/goal/active` 读取当前 active goal。

## 核心流程

1. 用户发送带 `planMode=true` 的聊天请求后，聊天执行链路会为当前工具调用绑定 `ChatToolExecutionContext.GovernanceContext`。该上下文包含 `userId`、`conversationId` 和 `runId`，由 `ChatStreamExecutionService` 或同步聊天执行入口在调用工具前写入线程上下文。缺少 `conversationId` 或 `userId` 时，目标工具会抛出中文 `BusinessException`，避免管理端探测或脱离会话的调用污染全局目标。
2. 模型调用 `get_goal` 时，`CodexBuiltinChatToolExecutor` 从工具参数读取 `goalId` 或 `goalKey`，两者都未指定时读取当前会话 active goal。执行器不再访问进程内 Map，而是把当前 `conversationId/userId` 传给 `ChatGoalService.getGoal`。服务按目标 ID、目标键或当前 active goal 查询仓储，所有查询都带会话和用户过滤；目标不存在时返回 `CHAT_TOOL_GOAL_NOT_FOUND`。
3. 模型调用 `create_goal` 时，执行器把标题、说明和 steps 数组转换为 `CreateGoalCommand`。`ChatGoalService` 先查同一会话当前用户是否已有 `ACTIVE` 目标，已有时直接返回该目标，保证同一会话最多一个 active goal。没有 active goal 时，服务生成 Snowflake ID，写入 `chat_goal`，把 steps 写入 `chat_goal_step`，并向 `chat_goal_event` 追加 `GOAL_CREATED` 事件。
4. 模型调用 `update_goal` 时，服务按 `goalId`、`goalKey` 或 active goal 定位目标，并更新标题、说明、状态和进度摘要。steps 入参非空时会替换该目标的步骤快照，旧步骤逻辑删除，新步骤按传入顺序重新保存。目标状态进入 `COMPLETED`、`BLOCKED` 或 `CANCELLED` 时写入终态时间，并追加 `GOAL_COMPLETED`、`GOAL_BLOCKED` 或 `GOAL_CANCELLED` 事件。
5. 目标创建或更新成功后，`ChatGoalService` 通过 `ChatStreamPublisher.publishGoal` 发布 `goal` SSE 事件。`SseChatStreamPublisher` 只负责把应用层 `ChatGoalView` 快照路由到会话 SSE 通道，`NoopChatStreamPublisher` 在测试和降级场景中忽略该事件。前端收到 `goal` 事件后可直接更新 active goal 状态。
6. 用户进入或切换会话时，查询接口 `GET /api/chat/conversations/{conversationId}/goal/active` 从登录态读取当前用户 ID，并委托 `ChatGoalService.getActiveGoal` 查询数据库。接口只返回 `ACTIVE` 目标；没有 active goal 时 `ApiResponse.success(null)`，前端据此清空目标浮窗。已完成或取消的目标仍保留在数据库和事件流水中，但刷新后不再作为当前目标展示。

## 关键文件

- `backend/src/main/resources/db/migration/V20260609_120000__create_chat_goal_tables.sql`：新增 `chat_goal`、`chat_goal_step`、`chat_goal_event` 三张目标表、索引和中文注释。
- `backend/src/main/resources/db/schema.sql`：同步维护目标表基线结构。
- `backend/src/main/java/com/codingx/chat/application/service/goal/ChatGoalService.java`：目标创建、查询、更新、步骤替换、事件追加和 SSE 发布编排。
- `backend/src/main/java/com/codingx/chat/domain/repository/ChatGoalRepository.java` 与 `ChatGoalRepositoryImpl.java`：目标、步骤、事件的持久化端口和 MyBatis 实现。
- `backend/src/main/java/com/codingx/tool/application/service/CodexBuiltinChatToolExecutor.java`：把目标工具从内存 Map 改为调用 `ChatGoalService`。
- `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatGoalController.java`：当前会话 active goal 查询接口。

## 关键数据结构

`chat_goal` 保存会话级目标本体，关键字段包括 `conversation_id`、`user_id`、`goal_key`、`status`、`progress_summary`、`created_run_id`、`updated_run_id` 和终态时间。`chat_goal_step` 保存目标步骤快照，关键字段包括 `goal_id`、`step_key`、`status`、`sort_no` 和 `detail`。`chat_goal_event` 保存追加事件，记录 `goal_id`、`conversation_id`、`run_id`、`event_type` 和 `payload_json`。

`ChatGoalView` 是后端目标工具 metadata、SSE goal 事件和查询接口的统一快照。所有 ID 在视图和响应层都转成字符串，避免前端读取 Snowflake Long 时发生精度丢失。目标状态支持 `ACTIVE`、`COMPLETED`、`BLOCKED`、`CANCELLED`，步骤状态支持 `PENDING`、`IN_PROGRESS`、`COMPLETED`、`BLOCKED`、`CANCELLED`。

## 测试与验证

- `cd backend && mvn -Dtest=ChatRuntimePersistenceStructureTest test`
- `cd backend && mvn -Dtest=ChatGoalServiceTest test`
- `cd backend && mvn -Dtest=CodexBuiltinChatToolExecutorTest -DfailIfNoTests=false test`
- `cd backend && mvn -Dtest=ChatGoalControllerTest test`
- `cd backend && mvn -Dtest=ChatToolSpecServiceTest test`
- `cd backend && mvn compile`
- `cd backend && mvn test`
