# Acceptance Criteria: 管理端 Dashboard 控制台改版

**Spec:** `docs/superpowers/specs/2026-05-28-155232-admin-dashboard-console-redesign-design.md`
**Date:** 2026-05-28
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 管理端 Dashboard 接口支持按窗口返回控制台聚合视图。 | API | 后端已启动，存在基础聊天、链路和配置数据。 | `GET /api/admin/chat/dashboard?window=24h` 返回 `success=true`，`data.window=24h`，且包含 `kpis`、`resources`、`performance`、`trendBuckets` 四个主要结构。 |
| AC-002 | Dashboard 趋势分桶必须按窗口粒度补齐空 bucket。 | Logic | 调用 Dashboard 聚合服务，窗口分别为 `24h`、`7d`、`30d`。 | `24h` 返回 24 个小时 bucket，`7d` 返回 7 个天 bucket，`30d` 返回 30 个天 bucket；即使某些桶无数据，对应字段也为 0。 |
| AC-003 | Dashboard 性能摘要必须基于当前窗口链路数据计算成功率、失败率和 P95 响应。 | Logic | 当前窗口内存在成功、失败和运行中的链路记录。 | 返回的 `performance.successRate/failureRate/runningRate` 与链路状态一致，`p95TraceDurationMs` 为窗口内已完成链路的 95 分位耗时。 |
| AC-004 | 管理端 API 层可按窗口请求新 Dashboard 结构。 | Logic | `AdminChatApi.getDashboard('7d')` 被调用。 | 请求地址包含 `/api/admin/chat/dashboard?window=7d`，解析结果可读取新结构中的嵌套字段。 |
| AC-005 | Dashboard 页面渲染参考控制台结构的核心指标区、主趋势区和右侧健康区。 | UI interaction | `AdminChatApi.getDashboard` 与 `getRuntimeDashboard` 返回有效聚合数据。 | 页面出现 4 张核心指标卡、1 块主趋势图、4 张小趋势图，以及右侧运行健康/质量快照/运营效率/运营洞察卡。 |
| AC-006 | Dashboard 时间窗口切换会重新拉取对应窗口的聚合数据。 | UI interaction | 页面已加载完成。 | 点击 `7d` 或 `30d` 后，`AdminChatApi.getDashboard` 以对应窗口值再次调用，页面标题与图表容器保持可见。 |
| AC-007 | Dashboard 图表统一由 AntV 组件渲染。 | UI interaction | Dashboard 页面已渲染。 | 主趋势图、四张趋势图和成功率环图对应的图表组件均来自 AntV 封装，不再使用页面内手写 SVG 趋势图。 |
| AC-008 | Layout 应呈现深色侧边栏和顶部工具栏，同时保持主内容区域纵向滚动。 | UI interaction | 打开任意管理端页面。 | 左侧导航为深色样式，顶部展示搜索栏与操作区，`main` 仍保留 `min-h-0 overflow-y-auto overflow-x-hidden`，不会导致双滚动。 |
| AC-009 | Dashboard 在亮色和暗色模式下都保持图表、文本和滚动条可读。 | UI interaction | 前端服务已启动，可切换主题。 | 亮色和暗色模式下，Dashboard 主容器背景非透明，图表文字/坐标轴/提示框可读，滚动条轨道与滑块使用主题色而非系统默认浅色。 |

## Coverage Check

- 接口结构、时间窗口、统计计算、前端渲染、图表实现、布局与主题验证均有明确验收项。
- 本次不覆盖知识库/RAG 专属指标，因为设计已明确排除。
