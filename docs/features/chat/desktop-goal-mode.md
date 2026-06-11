# 聊天桌面目标模式

## 功能用途

桌面端聊天工作台的“目标模式”用于让模型显式创建和跟进会话级目标。目标状态以后端数据库为权威来源，由 `get_goal`、`create_goal`、`update_goal` 工具读写，不再把普通聊天过程里的 `executionSteps` 推导成目标进度。

## 使用入口

用户在聊天输入区开启“目标模式”后，前端继续把本轮请求标记为 `planMode=true`。后端系统提示会让模型在需要长期跟进、多步骤开发、调试修复或用户明确要求目标管理时调用目标工具。页面刷新或切换会话时，应通过 `GET /api/chat/conversations/{conversationId}/goal/active` 读取当前 active goal。

管理员在管理端进入单个会话详情时，页面会在会话元信息和消息历史之间展示“目标记录”只读诊断区。该区域不提供目标编辑、删除或状态重置，只展示当前会话下所有未删除目标、对应步骤快照和事件流水，便于排查目标工具输入和状态变更结果。

## 核心流程

1. 用户发送带 `planMode=true` 的聊天请求后，聊天执行链路会为当前工具调用绑定 `ChatToolExecutionContext.GovernanceContext`。该上下文包含 `userId`、`conversationId` 和 `runId`，由 `ChatStreamExecutionService` 或同步聊天执行入口在调用工具前写入线程上下文。缺少 `conversationId` 或 `userId` 时，目标工具会抛出中文 `BusinessException`，避免管理端探测或脱离会话的调用污染全局目标。
2. 模型调用 `get_goal` 时，`CodexBuiltinChatToolExecutor` 从工具参数读取 `goalId` 或 `goalKey`，两者都未指定时读取当前会话 active goal。执行器不再访问进程内 Map，而是把当前 `conversationId/userId` 传给 `ChatGoalService.getGoal`。服务按目标 ID、目标键或当前 active goal 查询仓储，所有查询都带会话和用户过滤；查询未命中时这是正常空状态，工具返回 `exists=false` 和“当前会话没有活动目标”的中文提示，让模型继续按用户要求调用 `create_goal`，不会把聊天运行收口成失败。
3. 模型调用 `create_goal` 时，执行器把标题、说明和 steps 数组转换为 `CreateGoalCommand`。`ChatGoalService` 先查同一会话当前用户是否已有 `ACTIVE` 目标，已有时直接返回该目标，保证同一会话最多一个 active goal。没有 active goal 时，服务生成 Snowflake ID，写入 `chat_goal`，把 steps 写入 `chat_goal_step`，并向 `chat_goal_event` 追加 `GOAL_CREATED` 事件。
4. 模型调用 `update_goal` 时，服务按 `goalId`、`goalKey` 或 active goal 定位目标，并更新标题、说明、状态和进度摘要。steps 入参非空时会替换该目标的步骤快照，旧步骤逻辑删除，新步骤按传入顺序重新保存。目标状态进入 `COMPLETED`、`BLOCKED` 或 `CANCELLED` 时写入终态时间，并追加 `GOAL_COMPLETED`、`GOAL_BLOCKED` 或 `GOAL_CANCELLED` 事件。
5. 模型误用 `update_plan` 更新阶段计划时，`ChatApplicationService` 仍会把原始 plan 步骤保存为 `chat_execution_step` 并通过 `step` 事件展示在过程时间线。若本轮已经创建或读取到真实 active goal，服务会把 `update_plan.steps` 转换为 `update_goal.steps`，按步骤完成比例生成 `progressSummary`，并通过真实 `update_goal` 写入目标表。全部步骤完成时目标主状态写为 `COMPLETED`，存在阻塞或取消步骤时写为 `BLOCKED` 或 `CANCELLED`，否则保持 `ACTIVE`；这样右侧目标浮窗能收到 `goal` SSE，而普通聊天的 `update_plan` 不会误写目标。
6. 目标创建或更新成功后，`ChatGoalService` 通过 `ChatStreamPublisher.publishGoal` 发布 `goal` SSE 事件。`SseChatStreamPublisher` 只负责把应用层 `ChatGoalView` 快照路由到会话 SSE 通道，`NoopChatStreamPublisher` 在测试和降级场景中忽略该事件。前端收到 `goal` 事件后可直接更新 active goal 状态。
7. 目标模式执行型任务使用独立工具预算。`RuntimeSettingService` 先读取普通 `chat.tool.max_rounds`，普通聊天仍裁到最多 20 轮；当本轮是目标模式且用户明确要求执行、验证或提交时，再读取 `chat.tool.plan_execution_min_rounds`，默认 60，并取两者较大值。`AgentLoopCoordinator` 的全局安全上限为 60，因此大型 HTML 生成、验证和提交链路不会在第 20 轮 ACTIVE 进度后被误写为 `BLOCKED`；配置误填成超大值时仍会被裁剪。
8. 模型流在目标已创建后失败时，后端会区分工具是否已经真实落地。若失败发生在 write/edit 等完整工具调用之前，服务通过真实 `update_goal` 写入 `BLOCKED`，避免用户误以为半截工具参数已经写入文件；若上一轮工作工具已经成功返回且目标进度仍待同步，服务把异常作为系统提示回灌给模型，要求先 `update_goal` 同步已完成工作，再继续验证和提交。该恢复路径不会覆盖已成功的文件编辑或命令结果，也不会把任务提前标记为阻塞。
9. 用户进入或切换会话时，查询接口 `GET /api/chat/conversations/{conversationId}/goal/active` 从登录态读取当前用户 ID，并委托 `ChatGoalService.getActiveGoal` 查询数据库。接口只返回 `ACTIVE` 目标；没有 active goal 时 `ApiResponse.success(null)`，前端据此清空目标浮窗。已完成或取消的目标仍保留在数据库和事件流水中，但刷新后不再作为当前目标展示。
10. 管理员打开管理端会话详情 `GET /api/admin/chat/conversations/{conversationId}` 时，`AdminChatConversationService` 先读取会话本体和消息列表，再通过 `ChatGoalRepository.findAllByConversationId` 读取该会话所有未删除目标。服务随后批量读取这些目标的未删除步骤快照，并按会话读取事件流水，按 `goalId` 分组后挂载到每个目标响应。所有目标、步骤、事件 ID 和 runId 都在响应层转成字符串，前端 `TaskDetail` 只读渲染 `goals[].steps[]` 和 `goals[].events[]`；没有目标时显示“当前会话暂无目标记录”，不会影响消息列表展示。

## 关键文件

- `backend/src/main/resources/db/migration/V20260609_120000__create_chat_goal_tables.sql`：新增 `chat_goal`、`chat_goal_step`、`chat_goal_event` 三张目标表、索引和中文注释。
- `backend/src/main/resources/db/schema.sql`：同步维护目标表基线结构。
- `backend/src/main/java/com/codingx/chat/application/service/goal/ChatGoalService.java`：目标创建、查询、更新、步骤替换、事件追加和 SSE 发布编排。
- `backend/src/main/java/com/codingx/chat/domain/repository/ChatGoalRepository.java` 与 `ChatGoalRepositoryImpl.java`：目标、步骤、事件的持久化端口和 MyBatis 实现。
- `backend/src/main/java/com/codingx/admin/application/service/AdminChatConversationService.java`：管理端会话详情聚合目标主表、步骤快照和事件流水。
- `backend/src/main/java/com/codingx/tool/application/service/CodexBuiltinChatToolExecutor.java`：把目标工具从内存 Map 改为调用 `ChatGoalService`。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：目标模式工具循环、`update_plan` 到 `update_goal` 桥接、流失败恢复和终态收口保护。
- `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatGoalController.java`：当前会话 active goal 查询接口。
- `frontend/admin/src/pages/TaskDetail.tsx`：管理端会话详情目标记录只读展示区。

## 关键数据结构

`chat_goal` 保存会话级目标本体，关键字段包括 `conversation_id`、`user_id`、`goal_key`、`status`、`progress_summary`、`created_run_id`、`updated_run_id` 和终态时间。`chat_goal_step` 保存目标步骤快照，关键字段包括 `goal_id`、`step_key`、`status`、`sort_no` 和 `detail`。`chat_goal_event` 保存追加事件，记录 `goal_id`、`conversation_id`、`run_id`、`event_type` 和 `payload_json`。

`ChatGoalView` 是后端目标工具 metadata、SSE goal 事件和查询接口的统一快照。所有 ID 在视图和响应层都转成字符串，避免前端读取 Snowflake Long 时发生精度丢失。目标状态支持 `ACTIVE`、`COMPLETED`、`BLOCKED`、`CANCELLED`，步骤状态支持 `PENDING`、`IN_PROGRESS`、`COMPLETED`、`BLOCKED`、`CANCELLED`。

管理端会话详情响应新增 `goals` 数组。每个目标包含主表字段、`steps` 步骤快照数组和 `events` 事件流水数组；事件的 `payloadJson` 保留原始 JSON 文本，不在前端解析或改写。

## 测试与验证

- `cd backend && mvn -Dtest=ChatRuntimePersistenceStructureTest test`
- `cd backend && mvn -Dtest=ChatGoalServiceTest test`
- `cd backend && mvn -Dtest=CodexBuiltinChatToolExecutorTest -DfailIfNoTests=false test`
- `cd backend && mvn -Dtest=ChatGoalControllerTest test`
- `cd backend && mvn -Dtest=AdminChatConversationServiceTest test`
- `cd backend && mvn -Dtest=AdminChatConversationControllerTest test`
- `cd backend && mvn -Dtest=ChatToolSpecServiceTest test`
- `cd backend && mvn -Dtest=ChatApplicationServiceTest#planModeShouldBridgeUpdatePlanToGoalProgressWhenGoalExists+planModeShouldContinueAfterStreamFailureWhenWorkToolAlreadySucceeded+planModeExecutionShouldUseConfiguredBudgetBeyondTwentyRounds test`
- `cd frontend/admin && npm run test:run -- TaskDetail.test.tsx`
- `cd backend && mvn compile`
- `cd backend && mvn test`
