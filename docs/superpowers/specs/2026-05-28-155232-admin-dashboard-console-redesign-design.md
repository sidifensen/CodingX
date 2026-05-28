# 管理端 Dashboard 控制台改版设计

## 背景

当前仓库的管理端首页还是早期信息看板：上方是几张粗粒度指标卡，中下部是快捷入口、运行时卡片和静态表格，整体结构与 `D:\code\ragent` 管理端控制台存在明显差距。用户希望直接参考 `ragent` 的管理端 Dashboard 做一版同等完成度的控制台，并明确要求图表改用 AntV。

这次改版以 `ragent` 的信息编排和视觉层次为参考，但不复制其 RAG/知识库专属业务语义。当前仓库没有知识库召回质量、无知识率、文档分片等数据源，因此控制台必须使用 CodingX 自身真实存在的数据域：用户、会话、消息、工作空间、链路、技能、工具、专家、MCP、意图和示例问题。

本轮继续采用 autonomous 模式执行，不等待额外审批。

## 推荐方案

推荐采用“后台聚合接口 + 前端控制台组件化重构”的方案。

只做前端样式改版无法支撑类似参考图中的多块趋势图和右侧健康区，因为当前 `GET /api/admin/chat/dashboard` 仅返回几个总数，不足以驱动真实图表。相反，直接照搬 `ragent` 的数据结构又会把仓库里不存在的 RAG 语义强行引入。最稳妥的做法是：

1. 后端保留现有 `dashboard` / `runtime` 双接口模型，但把 `dashboard` 扩展为可按 `24h/7d/30d` 返回 KPI、性能摘要和时间分桶趋势。
2. 前端保留现有管理端路由结构，同时把 `Layout` 改成更接近参考图的深色导航 + 顶部工具栏。
3. Dashboard 页面拆成 hooks、图表组件和右侧状态组件，统一接入 AntV。

这样既能做到与参考图相近的控制台质感，又不会把当前系统没有实现的业务功能伪装成已有能力。

## 后端设计

### 接口

- 保持 `GET /api/admin/chat/dashboard`，新增可选查询参数 `window=24h|7d|30d`，默认 `24h`。
- 保持 `GET /api/admin/chat/runtime` 不变，继续承载队列与线程池快照。

### `dashboard` 聚合结构

- `window`：当前时间窗口。
- `generatedAt`：聚合快照生成时间。
- `kpis`：
  - `activeUserCount`：当前窗口内有会话或链路活跃的唯一用户数。
  - `conversationCount`：当前窗口内新建会话数。
  - `messageCount`：当前窗口内新建消息数。
  - `workspaceCount`：当前有效工作空间总数。
  - `traceCount`：当前窗口内链路数。
  - `runningTraceCount`：当前窗口内仍在运行的链路数。
- `resources`：
  - `skillCount`、`toolCount`、`expertCount`、`mcpCount`、`intentNodeCount`、`mappingCount`、`sampleQuestionCount`。
- `performance`：
  - `successRate`、`failureRate`、`runningRate`。
  - `avgTraceDurationMs`、`p95TraceDurationMs`。
- `trendBuckets[]`：
  - `label`、`bucketStart`、`conversationCount`、`messageCount`、`activeUserCount`、`traceCount`、`successCount`、`failedCount`、`avgDurationMs`。

### 聚合策略

- 基础总数通过各表 `deleted = 0` 计数获得。
- 趋势图不新增数据库结构，不引入 RAG 特有表；直接基于 `chat_conversation`、`chat_message`、`chat_trace_run` 在窗口内取数并在应用层按小时/天分桶。
- `activeUserCount` 采用当前窗口内会话创建人和链路用户的并集去重值。
- 性能摘要仅基于窗口内链路数据计算；当前没有完成链路时返回 0，由前端展示无数据状态。

### 代码结构

- 保留 `AdminChatDashboardService` 作为聚合入口。
- 新增与 Dashboard 相关的只读视图 record，避免沿用旧的平铺 `traceCount/intetNodeCount/...`。
- 聚合查询允许直接使用现有 Mapper 做只读统计，不强行把分析型查询塞回现有领域仓储。

## 前端设计

### 整体布局

- `Layout` 改为深色左侧导航栏、顶部全局工具栏和内容区三段式结构。
- 左侧导航保留当前仓库的路由入口，不引入 `ragent` 中的知识库/RAG 菜单。
- 顶部工具栏提供：
  - 一个仅作视觉与后续扩展预留的全局搜索框。
  - “返回聊天”按钮。
  - 当前用户菜单与主题切换入口。

### Dashboard 页面结构

- 顶部控制条：
  - 标题 `Dashboard`
  - `24h / 7d / 30d` 时间窗口切换
  - 刷新按钮
  - 快照时间
- 左侧主列：
  - 核心指标 4 卡：活跃用户、会话数、消息数、工作空间数
  - 流量概览：单大图，展示消息量主趋势，并辅以链路/会话信息
  - 趋势分析 4 图：会话趋势、活跃用户趋势、响应耗时趋势、质量趋势
- 右侧侧栏：
  - 运行健康卡：成功率环图、平均响应、P95 响应
  - 质量快照卡：失败率、运行中占比、队列压力
  - 运营效率卡：人均会话、单会话消息、每空间会话等派生指标
  - 运营洞察卡：根据性能与资源指标生成文字诊断

### 图表实现

- 图表统一使用 `@ant-design/plots`。
- 大图使用 `Area` / `Line` 类组合图，小图使用 `Line` / `Column`。
- 成功率环图使用 `Pie` donut 实现，不沿用手写 SVG。
- AntV 主题通过页面层封装，统一适配浅色/暗色文本、网格线、提示框与背景。

### 组件拆分

- `pages/dashboard/useAdminDashboard.ts`：数据拉取、窗口切换、加载和错误状态。
- `pages/dashboard/dashboardViewModel.ts`：派生指标、洞察文案和图表数据格式化。
- `pages/dashboard/components/*`：KPI、主图、趋势图、侧栏卡、空态/骨架。
- `Dashboard.tsx` 只保留页面编排。

## 测试策略

### 后端

- 控制器测试覆盖 `window` 参数透传和新的嵌套响应结构。
- 服务测试覆盖：
  - 24h/7d/30d 三种窗口的 bucket 数量和顺序。
  - 成功率、失败率、P95 耗时的计算。
  - 空数据时的 0 值行为。

### 前端

- API 测试覆盖 `getDashboard(window)` 的请求路径和新结构解析。
- 页面测试覆盖：
  - 时间窗口切换触发重新请求。
  - KPI 文案、右侧健康卡和洞察卡渲染。
  - AntV 图表容器渲染。
  - 错误态和骨架态。
- `Layout` 测试继续确保主内容滚动容器不回退。

### 浏览器验证

- 因为本次包含顶部工具栏、主题、图表和侧栏视觉改动，必须通过 `/web-access + CDP` 完成浏览器验证。
- 最小证据：
  - `http://localhost:5003/`
  - 亮色和暗色各至少一张截图，保存到 `logs/`
  - 校验 Dashboard 主容器背景非透明、图表文本可读、暗色滚动条不是系统默认浅色

## 自检

- 只复刻控制台信息架构与视觉层次，不引入不存在的 RAG 功能。
- 数据来自当前仓库已有表和运行时快照，不造假。
- 图表统一改为 AntV。
- 页面与布局都保持亮色/暗色可读。
