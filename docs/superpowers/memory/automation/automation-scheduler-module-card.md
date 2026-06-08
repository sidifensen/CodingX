---
type: module_card
title: automation-scheduler-module-card
summary: 记录用户端自动化/定时任务当前真实边界，区分已落地的任务管理与仍待扩展的真实执行链路。
tags:
  - automation
  - frontend
  - chat
owned_paths:
  - frontend/user/src/views/AutomationView.tsx
  - frontend/user/src/views/automation
  - frontend/user/src/api/automationApi.ts
  - backend/src/main/java/com/codingx/automation
  - backend/src/main/resources/db/migration/V20260608_181900__create_automation_task_table.sql
  - backend/src/main/resources/db/schema.sql
  - frontend/user/src/App.tsx
  - frontend/user/src/components/Sidebar.tsx
related_docs:
  - docs/superpowers/memory/automation/automation-scheduler-contract.md
  - docs/superpowers/memory/governance/governance-workbench-contract.md
entrypoints:
  - frontend/user/src/views/AutomationView.tsx
  - frontend/user/src/App.tsx
last_verified_commit: 814d8de580b14f18e051cb21c268529824627a1e
status: active
---

# Automation Scheduler Module Card

## Responsibilities

- 用户端 `/automation` 入口由 `App.tsx` 路由到 `AutomationView.tsx`，页面负责展示定时任务列表、空态、错误态和自定义创建弹窗。
- `frontend/user/src/views/automation` 承载任务列表、创建表单、格式化函数和请求 Hook，页面文件只做编排和状态连接。
- `AutomationTaskController` 暴露用户侧列表和手动创建接口，`AutomationTaskService` 负责校验用户、计划字段、创建来源、下一次执行时间和到期状态推进。
- 聊天入口通过 `AutomationTaskChatCreationService` 在当前会话内识别明确的计划创建请求，创建 `CHAT` 来源任务并保存助手确认消息，不跳转到自动化页面确认。
- `AutomationTaskScheduler` 已按固定间隔扫描到期启用任务，并把 `lastRunAt`、`lastRunStatus` 和 `nextRunAt` 推进委派给服务层；仓储写入使用旧 `nextRunAt` 条件做乐观认领，避免重叠扫描重复触发同一任务。当前仍未接入真实 AI 后台执行链路和执行历史明细。
- 治理模块的 `HookRuleService` 仍只覆盖任务生命周期 Hook 规则匹配，不应与自动化定时任务表混用。

## Entry Points

- `frontend/user/src/components/Sidebar.tsx`：用户切换到自动化页面的导航入口。
- `frontend/user/src/App.tsx`：`automation` 视图注册、路由解析和聊天工作区预加载控制。
- `frontend/user/src/views/AutomationView.tsx`：自动化页面编排入口。
- `frontend/user/src/views/automation/AutomationTaskCreateDialog.tsx`：手动创建任务的自定义弹窗。
- `frontend/user/src/views/automation/AutomationTaskList.tsx`：任务列表、空态和加载态。
- `frontend/user/src/api/automationApi.ts`：用户端自动化接口封装。
- `backend/src/main/java/com/codingx/automation/interfaces/controller/AutomationTaskController.java`：用户端列表和创建协议入口。
- `backend/src/main/java/com/codingx/automation/application/service/AutomationTaskChatCreationService.java`：聊天消息转自动化任务入口。
- `backend/src/main/java/com/codingx/automation/infrastructure/scheduler/AutomationTaskScheduler.java`：到期任务扫描入口。

## Invariants

- 用户端自动化页必须支持亮色和暗色主题，并复用项目主题令牌，不能写成只适配单主题的孤立页面。
- 页面级交互不能使用 `window.alert`、`window.confirm` 或 `window.prompt`，创建、编辑、删除确认必须使用项目内弹窗。
- 自动化任务与治理 Hook 要保持语义分离：定时任务描述“按计划主动创建或执行任务”，Hook 描述“任务生命周期事件触发后匹配动作”。
- 会话创建定时任务时必须校验会话归属、工作空间归属和用户登录态，不能从前端直接信任 `conversationId`。
- 手动创建定时任务时如果请求携带 `workspaceId`，必须在服务层校验该工作空间属于当前用户；空 `workspaceId` 只表示不绑定项目上下文。
- 聊天创建只在解析器命中明确创建意图和计划时间时短路普通聊天；解析器支持 `18:11`、`18点`、`十二点`、`两点半` 等受限时间写法，未命中时必须继续原模型/意图路由。
- 调度器只负责扫描和推进状态，不在调度层拼装聊天请求或重复实现任务筛选规则；下游真实执行只能消费成功认领后的触发快照。

## Extension Points

- 任务启停、删除、编辑和执行历史仍待扩展，必须继续按任务归属校验并返回统一中文 `ApiResponse.message`。
- 调度触发后的真实 AI 执行可复用聊天后台执行服务，但需要先定义执行记录、失败回写规则，以及真实执行链路自己的超时/重试保护。
- 会话创建解析器当前偏保守，后续可扩展自然语言日期、每周星期和一次性任务语义，但不能误伤普通学习计划类文本。
- 用户端可在列表行增加启停/删除/执行记录入口，仍需保持页面不预加载聊天首屏重型接口。

## Common Pitfalls

- 不要把 `governance_hook_rule` 扩展成定时任务表；这样会混淆生命周期事件和计划执行。
- 不要只做本地 state；刷新后用户创建的定时任务必须从后端恢复。
- 不要让自动化页直接预加载聊天首屏重型接口；当前 `App.tsx` 已避免在自动化页 bootstrap 聊天工作区。
- 不要绕过统一 `ApiResponse.message` 错误处理，页面错误应优先展示后端中文 message。
- 不要把“每天学习计划”这类普通任务内容误识别为创建自动化；解析器需要同时看到明确时间和创建/提醒语义。
- 不要只支持阿拉伯数字时间；用户常用“每天十二点”表达会话创建需求，必须通过解析器单测覆盖。
- 不要把调度状态推进误当成真实任务执行完成；当前 `TRIGGERED` 只表示已被扫描触发。
- 不要绕过 `markTriggeredIfDue` 直接 `save` 调度结果；否则多实例或重叠扫描会重新引入重复触发风险。
