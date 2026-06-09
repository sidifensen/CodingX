# 管理端控制台首页

## 功能用途

管理端控制台首页用于集中展示 CodingX 当前的用户活跃度、会话与消息趋势、链路运行健康和配置资产概览，替代旧版“指标卡 + 静态表格”首页。性能区域会同时展示平均响应趋势、P95 长尾指标解释和慢链路排查入口，帮助管理员区分整体均值与少量长尾请求。

## 使用入口

- 管理端首页路由：`/`
- 后端聚合接口：`GET /api/admin/chat/dashboard?window=24h|7d|30d`
- 运行时快照接口：`GET /api/admin/chat/runtime`
- 慢链路列表入口：`/traces?sort=duration_desc`

## 核心流程

1. 页面加载后通过 `AdminChatApi.getDashboard(window)` 与 `getRuntimeDashboard()` 拉取聚合数据和实时快照。
2. Dashboard 后端按窗口读取会话、消息和 Trace，并只把 `durationMs > 0` 的链路纳入平均响应、P95 和慢链路统计。
3. `AdminChatDashboardService` 将 P95 耗时、样本总数、P95 在升序样本中的 1 基排名、60 秒慢链路阈值和慢链路数量一起返回，前端不复算统计口径。
4. 页面左下趋势图标题显示“平均响应耗时趋势”，图例和 tooltip 也使用“平均响应耗时”，避免和右侧 P95 长尾指标混在一起理解。
5. 右侧 AI 性能区保留平均响应和 P95 响应，同时展示“62 条链路中，第 59 条耗时”这类排名说明，并显示 `> 60.0 秒：4 条` 的慢链路计数。
6. 用户点击“查看最慢链路”后进入 `/traces?sort=duration_desc`，Trace 列表页读取 `sort` 参数并通过 `AdminChatApi.listTraces` 传给后端。
7. Trace 后端只接受 `duration_desc` 白名单排序值；命中时按 `duration_ms`、`started_at`、`id` 倒序返回，未知排序值回退到默认开始时间倒序。

## 关键文件

- `backend/src/main/java/com/codingx/admin/application/service/AdminChatDashboardService.java`：Dashboard 聚合入口
- `backend/src/main/java/com/codingx/admin/application/service/AdminChatDashboardView*.java`：Dashboard 只读视图结构
- `backend/src/main/java/com/codingx/admin/interfaces/controller/AdminChatTraceController.java`：Trace 列表协议入口，接收慢链路排序参数
- `backend/src/main/java/com/codingx/chat/infrastructure/persistence/repository/runtime/ChatTraceRunRepositoryImpl.java`：Trace 列表默认排序和耗时倒序查询
- `frontend/admin/src/components/Layout.tsx`：管理端既有侧边栏与主题切换容器
- `frontend/admin/src/pages/Dashboard.tsx`：控制台页面编排
- `frontend/admin/src/pages/traces/TracePage.tsx`：Trace 列表读取 URL 排序参数并请求后端
- `frontend/admin/src/api/adminChatApi.ts`：Dashboard 与 Trace API 类型契约

## 关键数据结构

- `AdminDashboardKpiView`：活跃用户、会话、消息、工作空间、链路等核心指标
- `AdminDashboardResourceView`：技能、工具、专家、MCP、意图、映射、示例问题等资产指标
- `AdminDashboardPerformanceView`：成功率、失败率、运行中占比、平均响应、P95 响应、P95 样本数、P95 样本排名、慢链路阈值、慢链路数量
- `AdminDashboardTrendBucketView`：时间分桶趋势数据
- `AdminTraceRunQuery.sort`：当前仅支持 `duration_desc`，用于从 Dashboard 进入最慢链路列表

## 测试与验证方式

- 后端：`cd backend && mvn compile && mvn test`
- 前端：`cd frontend/admin && npm run build && npm run test:run`
- 浏览器：通过 `/web-access + CDP` 打开 `http://localhost:5003/`，验证亮色/暗色下的图表、文本和滚动条可读性，并输出截图到 `logs/`
