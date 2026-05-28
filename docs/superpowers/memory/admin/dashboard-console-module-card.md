---
type: module_card
title: admin-dashboard-console
summary: 管理端 Dashboard 控制台负责聚合运营态指标、趋势图和运行时健康视图
tags:
  - admin
  - dashboard
owned_paths:
  - backend/src/main/java/com/codingx/admin
  - frontend/admin/src/components
  - frontend/admin/src/pages
entrypoints:
  - backend/src/main/java/com/codingx/admin/interfaces/controller/AdminChatDashboardController.java
  - backend/src/main/java/com/codingx/admin/interfaces/controller/AdminChatRuntimeDashboardController.java
  - frontend/admin/src/components/Layout.tsx
  - frontend/admin/src/pages/Dashboard.tsx
last_verified_commit: df295563eedea6ac3e6b6e500b3487c9c7b12db7
status: active
---

# Admin Dashboard Console Module Card

## Responsibilities

- 管理端 Dashboard 聚合当前系统的用户、会话、消息、工作空间、链路和配置资产指标，输出可供前端控制台直接消费的只读视图。
- Dashboard 页面负责呈现接近 `ragent` 控制台的信息编排：左侧趋势区、右侧健康区和多块统计图，并统一支持亮色/暗色主题。
- 图表统一使用 AntV，避免本地手写 SVG 图表与样式逻辑分散在页面组件中。
- Dashboard 只展示当前项目真实可获得的数据域，不引入知识库召回、文档切片或 RAG 诊断等本仓库未实现的指标语义。

## Entry Points

- `AdminChatDashboardController` 暴露 `/api/admin/chat/dashboard` 聚合接口，按时间窗口返回 KPI、趋势分桶和控制台性能摘要。
- `AdminChatRuntimeDashboardController` 暴露 `/api/admin/chat/runtime` 运行时快照，提供队列门控和线程池状态。
- `frontend/admin/src/pages/Dashboard.tsx` 负责路由级数据装配，并把渲染拆分给 Dashboard 子组件。
- `frontend/admin/src/components/Layout.tsx` 继续负责既有管理端侧边栏、主题切换和主内容滚动容器。

## Invariants

- Dashboard 只能消费统一的 `AdminChatApi`，页面和图表组件不得自行拼接 `fetch` 请求。
- 趋势图必须建立在后端返回的真实时间分桶上，前端不得伪造固定样例序列冒充线上统计。
- 右侧健康栏的成功率、失败率、平均响应和 P95 响应必须来自当前窗口内链路数据或运行时快照，不能复用 RAG 项目中的知识库质量语义。
- Dashboard 改造后仍需保持主内容区域 `overflow-y-auto`，避免新增 header/transform 导致整个管理端出现额外滚动层。
- AntV 图表在暗色主题下必须显式覆盖网格线、文本、提示框和滚动容器颜色，禁止回退到默认浅色皮肤。

## Extension Points

- 若未来补充任务、工具调用、反馈或用户登录审计等更细的聚合指标，可在 Dashboard 聚合接口继续扩展分桶字段，而不是让前端串联多个分页接口自行拼统计。
- 若后续需要更高性能的统计查询，可把当前 Java 侧聚合逐步下沉到专用 Mapper SQL，但外部接口字段尽量保持稳定。
- 若未来新增 AntV 公共主题或通用图表容器，应抽到 `frontend/admin/src/components/dashboard/` 级别，避免页面内重复配置图表主题。

## Common Pitfalls

- 不要把 `ragent` 中的知识库、无知识率、召回率等术语直接搬过来；当前仓库没有对应数据源，必须用 CodingX 自身的数据域替换。
- 不要把 Dashboard 继续堆在单个 `Dashboard.tsx` 中；布局、图表和派生逻辑必须拆分，避免页面文件失控增长。
- 不要使用浏览器原生弹窗承载控制台交互；筛选、刷新和用户菜单需沿用项目内现有组件模式。
- 不要忽略暗色滚动条和 hover/focus 可读性；控制台大量使用滚动容器和浅底图表，暗色模式下最容易退化。
