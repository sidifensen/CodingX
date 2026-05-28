---
type: contract
title: admin-dashboard-console-contract
summary: 管理端 Dashboard 控制台 API 与前端数据消费契约
tags:
  - admin
  - dashboard
owned_paths:
  - backend/src/main/java/com/codingx/admin/interfaces/controller
  - backend/src/main/java/com/codingx/admin/application/service
  - frontend/admin/src/api/adminChatApi.ts
  - frontend/admin/src/pages
related_docs:
  - docs/superpowers/memory/admin/dashboard-console-module-card.md
entrypoints:
  - backend/src/main/java/com/codingx/admin/interfaces/controller/AdminChatDashboardController.java
  - backend/src/main/java/com/codingx/admin/interfaces/controller/AdminChatRuntimeDashboardController.java
last_verified_commit: df295563eedea6ac3e6b6e500b3487c9c7b12db7
status: active
---

# Admin Dashboard Console Contract

## Scope

本契约覆盖管理端控制台首页聚合接口，以及前端消费这些聚合数据生成 KPI、趋势图和健康摘要的规则。它不覆盖知识库/RAG 相关运营指标，也不改变现有管理端会话、工作空间、工具、技能等单页接口。

## Producers And Consumers

- Producer: `GET /api/admin/chat/dashboard`，按 `window` 时间窗口返回控制台所需的聚合视图。
- Producer: `GET /api/admin/chat/runtime`，返回当前队列门控和线程池实时快照。
- Consumer: `AdminChatApi.getDashboard(window)`，负责统一拼接 `window` 查询参数、解析 `ApiResponse.message` 并返回类型化结果。
- Consumer: `AdminChatApi.getRuntimeDashboard()`，负责返回运行时快照。
- Consumer: Dashboard 页面与 AntV 图表组件，只读取集中封装后的结构，不自行推断后端字段名。

## Interface Rules

- Dashboard 路径：`GET /api/admin/chat/dashboard`
- 查询参数：
  - `window`：可选，支持 `24h`、`7d`、`30d`，默认 `24h`
- Dashboard 返回字段：
  - `window`：当前窗口值
  - `generatedAt`：后端生成快照时间
  - `kpis.activeUserCount`：当前窗口内有会话或链路活跃的唯一用户数
  - `kpis.conversationCount`：当前窗口内创建的会话数
  - `kpis.messageCount`：当前窗口内创建的消息数
  - `kpis.workspaceCount`：当前有效工作空间总数
  - `kpis.traceCount`：当前窗口内链路运行数
  - `kpis.runningTraceCount`：当前窗口内处于 RUNNING 状态的链路数
  - `resources.skillCount/toolCount/expertCount/mcpCount/intentNodeCount/mappingCount/sampleQuestionCount`：当前有效配置资产总数
  - `performance.successRate/failureRate/runningRate`：基于当前窗口链路状态计算的百分比
  - `performance.avgTraceDurationMs/p95TraceDurationMs`：当前窗口已完成链路的平均与 P95 耗时
  - `trendBuckets[]`：按窗口自动选择小时或天粒度的分桶数组，包含 `label`、`bucketStart`、`conversationCount`、`messageCount`、`activeUserCount`、`traceCount`、`successCount`、`failedCount`、`avgDurationMs`
- Runtime 路径：`GET /api/admin/chat/runtime`
- Runtime 返回字段保持现有双视图：
  - `queue.mode/maxConcurrent/activeCount/waitingCount/availablePermits`
  - `executor.streamActiveCount/streamPoolSize/streamQueueSize/streamQueueRemainingCapacity/searchActiveCount/searchPoolSize/searchQueueSize/searchQueueRemainingCapacity`

## Invariants

- `trendBuckets` 必须按时间升序返回，且 bucket 数量与窗口对应：`24h` 按小时，`7d` 与 `30d` 按天。
- 没有数据的 bucket 也必须补齐为 0，前端图表不能依赖“缺 bucket 表示 0”。
- `successRate + failureRate + runningRate` 允许因四舍五入不等于 100，但原始计算必须基于同一个窗口内的链路总数。
- `p95TraceDurationMs` 没有已完成链路时返回 `0`，由前端显示为 `-` 或无数据状态。
- `workspaceCount` 与 `resources.*Count` 不随窗口变化，只统计 `deleted = 0` 的有效记录。
- 前端 UI 可以根据这些字段生成文案、状态色和洞察卡，但不得改写后端错误消息语义。

## Compatibility Notes

- Dashboard 视觉参考来自 `D:\\code\\ragent`，但数据契约只使用当前仓库真实字段，不兼容 `ragent` 的 knowledge base、quality/no-doc 等专有接口。
- 若未来新增窗口枚举或更细粒度趋势，必须同步扩展 `AdminChatApi` 类型与前端切换组件，避免字符串散落。
