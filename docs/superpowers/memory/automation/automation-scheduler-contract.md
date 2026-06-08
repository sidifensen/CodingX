---
type: contract
title: automation-scheduler-contract
summary: 记录自动化定时任务从用户创建、会话创建到后端调度扫描的真实契约和剩余缺口。
tags:
  - automation
  - contract
  - scheduler
owned_paths:
  - frontend/user/src/views/AutomationView.tsx
  - frontend/user/src/views/automation
  - frontend/user/src/api/automationApi.ts
  - backend/src/main/java/com/codingx/automation
  - backend/src/main/resources/db/schema.sql
  - backend/src/main/resources/db/migration
related_docs:
  - docs/superpowers/memory/automation/automation-scheduler-module-card.md
  - docs/superpowers/memory/chat/background-stream-resume-contract.md
entrypoints:
  - frontend/user/src/views/AutomationView.tsx
last_verified_commit: 814d8de580b14f18e051cb21c268529824627a1e
status: active
---

# Automation Scheduler Contract

## Scope

该契约覆盖用户端自动化定时任务的当前真实边界：手动创建、从会话创建、列表展示、计划字段校验和到期扫描状态推进。当前仓库已经具备 `automation_task` 表、用户侧列表/创建接口、聊天创建短路分支和 Spring 定时扫描入口；尚未交付真实 AI 后台执行、执行历史、启停/删除/编辑协议。

## Producers And Consumers

- Producer：`AutomationTaskService` 负责校验用户、计划字段、来源会话、创建来源，创建任务并推进到期任务状态。
- Producer：`AutomationTaskChatCreationService` 负责从聊天会话中创建 `CHAT` 来源任务，并生成助手确认消息。
- Producer：`AutomationTaskScheduler` 负责扫描启用且到期的任务，并把状态推进委派给服务层。
- Consumer：用户端 `AutomationView` 消费任务列表、创建结果、启停结果和最近执行状态。
- Consumer：`ChatApplicationService` 在命中自动化创建意图时消费创建结果，保存助手消息并发布完成 SSE。

## Interface Rules

- 手动创建入口 `POST /api/automation/tasks` 至少需要 `name`、`prompt`、`scheduleType` 和计划时间字段，后端从 Sa-Token 补齐当前用户；若请求显式携带 `workspaceId`，服务层必须通过 `WorkspaceRepository.ensureOwnedByUser` 校验当前用户归属。
- 列表入口 `GET /api/automation/tasks` 只返回当前用户未删除任务，默认按启用状态、下一次执行时间和更新时间排序。
- 会话创建入口不新增页面确认协议；`ChatApplicationService` 在当前会话已完成归属校验后，把会话、用户消息和当前用户 ID 交给自动化创建服务。
- 解析器只有在明确计划时间和创建/提醒语义同时存在时才返回创建参数；每日时间支持阿拉伯数字时间和受限中文整点/半点表达，例如 `18:11`、`18点`、`十二点`、`两点半`。未命中时普通聊天必须继续模型/意图路由。
- 当前版本没有启停、删除、更新接口；后续扩展必须基于任务归属校验，任务不存在或不属于当前用户时通过统一异常处理返回中文 `ApiResponse.message`。
- 调度扫描当前只推进 `lastRunAt`、`lastRunStatus` 和 `nextRunAt`；写入时必须用扫描时的旧 `nextRunAt` 做条件更新，避免重叠扫描重复触发同一到期任务。真实执行失败记录和执行历史仍需后续定义。

## State And Schema Rules

- 自动化任务需要持久化计划配置、来源类型、来源会话、工作空间、启用状态、最近执行时间、下一次执行时间、最近执行结果和审计时间。
- `scheduleType` 第一版限定为 `DAILY`、`WEEKLY`、`ONCE`，覆盖“每天 + 时间”、每周和一次性计划。
- `enabled` 控制定时扫描是否触发；删除应使用逻辑删除，避免历史执行关系断裂。
- 最近执行结果只作为列表摘要，当前 `TRIGGERED` 表示调度已扫描触发，不代表 AI 任务真实执行完成。

## Invariants

- 数据库变更必须同时提交迁移脚本和 `backend/src/main/resources/db/schema.sql`，所有表和字段必须有中文注释。
- 自动化任务的创建和调度必须与 `governance_hook_rule` 语义分离，不允许复用 Hook 规则字段表达计划时间。
- 从会话创建时，后端必须基于当前已归属校验的 `ChatConversation` 读取工作空间和会话 ID，不能信任前端额外传入。
- 调度器不得在多实例或重复扫描时重复执行同一到期任务；当前仓储通过 `id + enabled + deleted + old next_run_at` 条件更新完成乐观认领，只有成功认领的任务才会返回给下游执行扩展点。
- 前端自动化页必须覆盖空态、加载态、错误态、创建弹窗、保存禁用态、亮暗主题和滚动条样式。

## Compatibility Notes

- 当前工作区可能存在大量未提交的 governance、chat 和前端改动，扩展自动化时必须只触碰必要文件，避免格式化或回滚他人改动。
- `AutomationView.tsx` 已替换为 API 驱动的“定时任务”管理视图，后续仍需保留 `App.tsx` 对自动化页不预加载聊天首屏接口的性能约束。
- 若需要浏览器验证，必须通过 `/web-access` skill 和 CDP 保存截图到 `logs/`。
