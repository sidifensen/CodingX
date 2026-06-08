# 自动化定时任务

## 功能用途

自动化定时任务用于把用户的周期性 AI 工作托管给系统。用户可以在 `/automation` 页面手动创建任务，也可以在聊天会话中直接发送明确的计划指令，例如“每天 18:11 帮我总结项目状态”或“每天十二点给我推送 AI 新闻”，系统会在当前会话内创建任务并追加助手确认消息。

## 使用入口

- 用户前端 `/automation`：查看当前账号未删除任务、打开自定义弹窗、提交手动任务。
- 用户聊天流式接口：当用户消息包含明确的创建意图和计划时间时，直接在当前会话内创建 `CHAT` 来源任务。
- 后端调度器：按 `automation.scheduler.fixed-delay-ms` 固定间隔扫描到期任务，默认 60000 毫秒。

## 核心流程

1. 手动创建时，前端 `AutomationView` 先通过 `AuthStorage` 读取当前 token，再由 `useAutomationTasks` 调用 `AutomationApi.listTasks` 加载列表。用户点击“新建定时任务”后打开项目内自定义弹窗，表单要求 `name`、`prompt` 和计划时间齐备才允许提交。提交时前端组装 `scheduleType`、`scheduleTime`、`scheduleDayOfWeek`、`onceExecuteAt` 与 `workspaceId=null`，通过 `POST /api/automation/tasks` 发给后端。后端错误经 `ApiResponseParser` 保留原始中文 `message`，页面直接展示，不使用浏览器原生弹窗。
2. 协议层 `AutomationTaskController` 从 Sa-Token 读取登录用户 ID，只负责请求字段适配和 `ApiResponse` 包装。控制器把 `scheduleType` 字符串交给 `AutomationTaskService.parseScheduleType` 校验，再调用 `createManualTask`。若用户未登录、任务名称为空、需求为空、显式 `workspaceId` 不属于当前用户或执行时间非法，业务异常会进入全局异常处理器并返回中文错误结构。成功时接口返回 `AutomationTaskResponse`，包含来源、计划、启用状态、下一次运行时间和工作空间信息。
3. 应用服务 `AutomationTaskService` 统一处理手动和聊天创建。服务先校验 `userId`，显式 `workspaceId` 会通过 `WorkspaceRepository.ensureOwnedByUser` 做当前用户归属校验，空工作空间表示不绑定项目上下文。随后服务校验 `name`、`prompt`，再按 `DAILY`、`WEEKLY`、`ONCE` 解析计划字段：每日/每周必须有 `HH:mm`，每周星期限制为 1 到 7，一次性任务必须落在未来。服务为新任务生成雪花 ID，写入 `sourceType`、`sourceConversationId`、`enabled=true`、`deleted=false`、`lastRunStatus=PENDING`，并计算 `nextRunAt`。仓储按用户隔离查询，只返回当前用户未删除任务，并按启用状态、下一次运行时间和更新时间排序。
4. 会话创建时，`ChatApplicationService` 在保存用户消息并发布用户 SSE 后调用可选的 `AutomationTaskChatCreationService`。该服务用 `AutomationTaskIntentParser` 做保守识别，只有同时出现明确计划和任务创建语义时才返回创建参数；每日时间支持 `18:11`、`18点`、`十二点`、`两点半` 这类常见写法，但普通聊天或“帮我制定每天学习计划”这类无创建意图文本会返回空结果并继续原聊天路径。创建成功后聊天服务保存一条助手消息，内容包含“已创建自动化任务”和计划时间，并发布助手完成事件。整个过程停留在当前聊天路由，不跳转 `/automation`，也不要求用户二次确认。
5. 调度阶段由 `AutomationTaskScheduler.scanDueTasks` 定时触发，读取同一批扫描的当前时间后调用 `AutomationTaskService.triggerDueTasks`。服务从仓储查找 `enabled=1`、`deleted=0`、`nextRunAt <= now` 的任务，为每个候选先计算触发后的 `lastRunAt=now`、`lastRunStatus=TRIGGERED` 和新 `nextRunAt`。写入时仓储使用扫描时的旧 `nextRunAt` 做条件更新，只有仍停留在同一到期快照的任务才会被本轮认领；若重叠扫描已推进该任务，本轮会跳过并不返回重复触发快照。周期任务会把 `nextRunAt` 推进到未来下一次，一次性任务会清空下一次执行时间并停用。当前版本只完成调度状态推进，后续真实 AI 执行链路可基于成功认领的触发快照继续编排。
6. 前端创建成功后不信任本地临时结果，而是重新调用列表接口刷新页面。任务列表展示名称、需求摘要、来源图标、计划文案、下一次运行时间和运行状态。空列表展示“暂无定时任务”和手动创建入口，同时提示也可在聊天中创建。暗色模式使用全局主题令牌，弹窗、列表和错误条均为不透明背景，保证文本和边框可读。

## 关键文件

- `backend/src/main/java/com/codingx/automation/application/service/AutomationTaskService.java`：创建、列表、计划校验和到期推进。
- `backend/src/main/java/com/codingx/workspace/domain/repository/WorkspaceRepository.java`：自动化创建复用的工作空间归属校验端口。
- `backend/src/main/java/com/codingx/automation/application/service/AutomationTaskIntentParser.java`：聊天文本中的自动化创建意图解析。
- `backend/src/main/java/com/codingx/automation/application/service/AutomationTaskChatCreationService.java`：会话内创建任务并生成助手确认消息。
- `backend/src/main/java/com/codingx/automation/interfaces/controller/AutomationTaskController.java`：用户端任务列表和手动创建接口。
- `backend/src/main/java/com/codingx/automation/infrastructure/scheduler/AutomationTaskScheduler.java`：Spring 定时扫描入口。
- `backend/src/main/resources/db/migration/V20260608_181900__create_automation_task_table.sql`：自动化任务表迁移。
- `backend/src/main/resources/db/schema.sql`：自动化任务表基线结构。
- `frontend/user/src/views/AutomationView.tsx`：自动化页面编排。
- `frontend/user/src/views/automation/AutomationTaskCreateDialog.tsx`：手动创建任务弹窗。
- `frontend/user/src/views/automation/AutomationTaskList.tsx`：任务列表、加载态和空态。
- `frontend/user/src/api/automationApi.ts`：自动化接口封装和错误解析。

## 关键数据结构

- `automation_task.source_type`：`MANUAL` 表示页面创建，`CHAT` 表示聊天创建。
- `automation_task.source_conversation_id`：聊天创建任务的来源会话，手动创建为空。
- `automation_task.schedule_type`：`DAILY`、`WEEKLY`、`ONCE`。
- `automation_task.schedule_time`：每日或每周固定执行时间，格式 `HH:mm`。
- `automation_task.schedule_day_of_week`：每周执行星期，1 表示周一，7 表示周日。
- `automation_task.once_execute_at`：一次性任务执行时间。
- `automation_task.next_run_at`：调度器筛选到期任务的依据。
- `automation_task.last_run_status`：最近触发状态摘要，当前使用 `PENDING` 和 `TRIGGERED`。
- `automation_task.enabled`、`automation_task.deleted`：调度和列表过滤字段。

## 验证方式

- 后端目标测试：`mvn -Dtest=AutomationTaskServiceTest test`、`mvn -Dtest=ChatApplicationServiceTest,AutomationTaskControllerTest test`、`mvn -Dtest=AutomationTaskIntentParserTest test`、`mvn -Dtest=AutomationTaskSchedulerTest test`。
- 前端目标测试：`npm run test:run -- AutomationView.test.tsx`。
- 完整验证：后端 `mvn compile`、`mvn test`，前端 `npm run build`、`npm run test:run`。
- 浏览器验证：启动后端和用户前端，打开 `http://localhost:5002/automation`，通过 CDP 截图和计算样式确认暗色弹窗背景不透明、文本可读；本次截图证据保存为 `logs/automation-dark-create-dialog.png`。
