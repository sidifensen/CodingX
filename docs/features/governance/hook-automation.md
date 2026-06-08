# Hook 自动化规则

## 功能用途

Hook 自动化规则用于在聊天任务生命周期中匹配可配置动作，作为桌面通知、宠物联动、本地脚本或 Webhook 的统一入口。当前版本只负责管理和匹配规则，不再写入 `governance_hook_audit` 审计流水。

## 使用入口

- 管理端「治理中心」的 Hook 页签维护规则编码、触发点、条件关键字、动作类型和动作配置 JSON。
- 聊天后台任务在开始、失败、需要确认和完成时触发 Hook 规则匹配。

## 核心流程

1. 管理员在治理中心创建或编辑 Hook 规则后，前端调用 `/api/admin/governance/hook-rules` 保存 `hookCode`、`triggerPoint`、`conditionKeyword`、`actionType` 和 `actionConfigJson`；后端 `HookRuleService` 将空动作类型默认补为 `DESKTOP_NOTIFY`。
2. 后台任务派发进入执行线程后，`ChatStreamExecutionService` 触发 `BEFORE_TASK_START`；如果运行门控拒绝、应用服务异常或本地运行异常，则触发 `TASK_FAILED`，并把会话 ID、运行 ID 和错误摘要传给 Hook 规则服务。
3. 工具权限策略返回需要用户确认时，`ChatApplicationService` 捕获 `GOVERNANCE_PERMISSION_CONFIRM_REQUIRED`，触发 `TASK_CONFIRM_REQUIRED`；普通工具运行失败只走任务失败收口，不再写工具前后审计 Hook。
4. 聊天执行正常收口并写入 `COMPLETED` 运行记录时，`ChatApplicationService` 触发 `TASK_COMPLETED`；`HookRuleService.trigger` 按触发点读取启用规则，再用条件关键字过滤并按排序返回命中规则。
5. Hook 匹配失败或规则服务异常只写应用日志，不中断聊天流；后续桌面通知、宠物或脚本执行器可消费返回的规则动作配置。

## 关键文件

- `backend/src/main/java/com/codingx/governance/application/service/HookRuleService.java`：Hook 规则匹配与 CRUD。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatStreamExecutionService.java`：任务开始和失败触发点。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：需要确认和任务完成触发点。
- `frontend/admin/src/pages/GovernanceCenterPage.tsx`：管理端 Hook 规则配置页签。
- `backend/src/main/resources/db/migration/V20260608_174801__remove_hook_audit_and_seed_automation_hooks.sql`：删除旧审计表并内置四个任务生命周期规则。

## 关键数据结构

- `governance_hook_rule.trigger_point`：固定覆盖 `BEFORE_TASK_START`、`TASK_CONFIRM_REQUIRED`、`TASK_FAILED`、`TASK_COMPLETED`。
- `governance_hook_rule.action_type`：默认 `DESKTOP_NOTIFY`，可扩展为 `PET_EVENT`、`LOCAL_SCRIPT`、`WEBHOOK`。
- `governance_hook_rule.action_config_json`：动作配置 JSON，由后续执行器解释。

## 验证方式

- 后端定向测试：`mvn "-Dtest=HookRuleServiceTest,HookAutomationSchemaCompatibilityTest,ChatStreamExecutionServiceTest" test`。
- 管理端验证：`npm run build`、`npm run test:run`。
- 浏览器验证：打开 `http://localhost:5003/governance` 的 Hook 页签，确认四个内置任务 Hook 规则展示且页面不再显示 Hook 审计。
