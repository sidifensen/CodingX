# Hook 自动化规则

## 功能用途

Hook 自动化规则用于在聊天任务生命周期中匹配可配置动作，作为桌面通知、宠物联动、本地脚本或 Webhook 的统一入口。当前版本不再写入 `governance_hook_audit` 审计流水；`DESKTOP_NOTIFY` 动作已接入用户会话 SSE 和 Electron 桌面宿主，可在 Windows 系统通知中提醒任务开始、失败、等待确认或完成。

## 使用入口

- 管理端「治理中心」的 Hook 页签维护规则编码、触发点、条件关键字、动作类型和动作配置 JSON。
- 聊天后台任务在开始、失败、需要确认和完成时触发 Hook 规则匹配。
- 桌面端用户前端收到 `hook-notification` SSE 后，会先读取本机设置页的系统通知开关，再经 `codingxHost.showDesktopNotification` 调用 Windows 系统通知。

## 核心流程

1. 管理员在治理中心创建或编辑 Hook 规则后，前端调用 `/api/admin/governance/hook-rules` 保存 `hookCode`、`triggerPoint`、`conditionKeyword`、`actionType` 和 `actionConfigJson`；后端 `HookRuleService` 将空动作类型默认补为 `DESKTOP_NOTIFY`。
2. 后台任务派发进入执行线程后，`ChatStreamExecutionService` 触发 `BEFORE_TASK_START`；如果运行门控拒绝、应用服务异常或本地运行异常，则触发 `TASK_FAILED`，并把会话 ID、运行 ID 和错误摘要传给 Hook 规则服务。
3. 工具权限策略返回需要用户确认时，`ChatApplicationService` 捕获 `GOVERNANCE_PERMISSION_CONFIRM_REQUIRED`，触发 `TASK_CONFIRM_REQUIRED`；普通工具运行失败只走任务失败收口，不再写工具前后审计 Hook。
4. 聊天执行正常收口并写入 `COMPLETED` 运行记录时，`ChatApplicationService` 触发 `TASK_COMPLETED`；`HookRuleService.trigger` 按触发点读取启用规则，再用条件关键字过滤并按排序返回命中规则。命中规则会写中文应用日志，日志字段包含规则编码、触发点、动作类型、会话 ID、运行 ID 和工具编码。
5. 当命中规则的 `actionType` 为 `DESKTOP_NOTIFY` 且存在来源会话 ID 时，`HookRuleService` 解析 `actionConfigJson` 中的 `title` 和 `body`，组装 `hook-notification` SSE 载荷并通过 `ChatStreamPublisher.publishHookNotification` 推给对应会话。配置为空或 JSON 解析失败时使用中文兜底标题 `CodingX 通知` 和正文 `有一项后台任务状态已更新`，解析异常只写中文警告日志，不中断聊天任务。非 `DESKTOP_NOTIFY` 动作仍只作为匹配结果返回，避免宠物、脚本和 Webhook 被误弹成系统通知。
6. 用户前端 `useChatWorkspace` 只在 `hostType=desktop`、`desktopNotifications=true` 且本机 `codingx.desktop.notifications.enabled` 未关闭时调用 `resolveHostBridge().showDesktopNotification`，Web fallback 或关闭本地开关时静默忽略。设置页 `/settings` 由个人菜单进入，开关状态只写入当前浏览器 localStorage，不写数据库也不随账号同步。Electron preload 把调用转成 `host:show-desktop-notification` IPC，主进程用 `Notification` 展示 Windows 系统通知；系统不支持通知或主窗口不存在时返回 `false`，通知点击只聚焦现有窗口，不擅自切换会话路由。

## 关键文件

- `backend/src/main/java/com/codingx/governance/application/service/HookRuleService.java`：Hook 规则匹配与 CRUD。
- `backend/src/main/java/com/codingx/chat/domain/port/ChatStreamPublisher.java`：聊天流事件发布端口，包含 Hook 通知事件方法。
- `backend/src/main/java/com/codingx/chat/infrastructure/stream/SseChatStreamPublisher.java`：把 Hook 通知发布为 `hook-notification` SSE。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatStreamExecutionService.java`：任务开始和失败触发点。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：需要确认和任务完成触发点。
- `frontend/admin/src/pages/GovernanceCenterPage.tsx`：管理端 Hook 规则配置页签。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：消费 `hook-notification` SSE 并按宿主能力和本地通知开关转交桌面通知。
- `frontend/user/src/views/SettingsView.tsx`、`frontend/user/src/host/desktopNotificationPreference.ts`：设置页和本地系统通知偏好缓存。
- `frontend/user/src/host/bridge.ts`、`frontend/user/src/host/types.ts`：用户前端宿主桥接协议和 Web fallback。
- `frontend/desktop/src/main.ts`、`frontend/desktop/src/preload.ts`、`frontend/desktop/src/desktopNotification.ts`：Electron IPC、Windows 系统通知调用和通知载荷清洗。
- `backend/src/main/resources/db/migration/V20260608_174801__remove_hook_audit_and_seed_automation_hooks.sql`：删除旧审计表并内置四个任务生命周期规则。

## 关键数据结构

- `governance_hook_rule.trigger_point`：固定覆盖 `BEFORE_TASK_START`、`TASK_CONFIRM_REQUIRED`、`TASK_FAILED`、`TASK_COMPLETED`。
- `governance_hook_rule.action_type`：默认 `DESKTOP_NOTIFY`，可扩展为 `PET_EVENT`、`LOCAL_SCRIPT`、`WEBHOOK`。
- `governance_hook_rule.action_config_json`：动作配置 JSON；桌面通知当前识别 `title` 和 `body`，缺失或无效时使用中文兜底文案。
- `hook-notification` SSE：包含 `type`、`hookCode`、`hookName`、`triggerPoint`、`actionType`、`conversationId`、`runId`、`toolCode`、`title`、`body` 和 `contextText`。
- `codingx.desktop.notifications.enabled`：用户本机 localStorage 偏好，未设置或值为 `true` 时允许桌面通知，值为 `false` 时任务 Hook 通知只静默消费。

## 验证方式

- 后端定向测试：`mvn "-Dtest=HookRuleServiceTest,SseChatStreamPublisherTest,HookAutomationSchemaCompatibilityTest,ChatStreamExecutionServiceTest" test`。
- 用户前端目标测试：`npm run test:run -- desktopNotificationPreference.test.ts App.test.tsx useChatWorkspace.test.ts -t "系统通知|hook-notification"`。
- 桌面端目标测试：`npm run test:run`。
- 管理端验证：`npm run build`、`npm run test:run`。
- 浏览器验证：打开 `http://localhost:5003/governance` 的 Hook 页签，确认四个内置任务 Hook 规则展示且页面不再显示 Hook 审计。
