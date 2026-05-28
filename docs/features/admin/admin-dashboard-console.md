# 管理端控制台首页

## 功能用途

管理端控制台首页用于集中展示 CodingX 当前的用户活跃度、会话与消息趋势、链路运行健康和配置资产概览，替代旧版“指标卡 + 静态表格”首页。

## 使用入口

- 管理端首页路由：`/`
- 后端聚合接口：`GET /api/admin/chat/dashboard?window=24h|7d|30d`
- 运行时快照接口：`GET /api/admin/chat/runtime`

## 核心流程

1. 页面加载后通过 `AdminChatApi.getDashboard(window)` 与 `getRuntimeDashboard()` 拉取聚合数据和实时快照。
2. `useAdminDashboard` 统一管理窗口切换、刷新、加载和错误状态。
3. `dashboardViewModel` 把原始聚合字段转换为 KPI、AntV 图表数据、质量快照和洞察文案。
4. 页面按“顶部控制条 + 左侧主图/趋势区 + 右侧健康侧栏”编排渲染。
5. Layout 继续复用原有管理端侧边栏与主题切换，只让 Dashboard 页面自身承载控制台内容。

## 关键文件

- `backend/src/main/java/com/codingx/admin/application/service/AdminChatDashboardService.java`：Dashboard 聚合入口
- `backend/src/main/java/com/codingx/admin/application/service/AdminChatDashboardView*.java`：Dashboard 只读视图结构
- `frontend/admin/src/components/Layout.tsx`：管理端既有侧边栏与主题切换容器
- `frontend/admin/src/pages/Dashboard.tsx`：控制台页面编排
- `frontend/admin/src/pages/dashboard/useAdminDashboard.ts`：控制台数据拉取
- `frontend/admin/src/pages/dashboard/dashboardViewModel.ts`：派生指标与洞察
- `frontend/admin/src/pages/dashboard/components/*`：AntV 图表与侧栏卡片组件

## 关键数据结构

- `AdminDashboardKpiView`：活跃用户、会话、消息、工作空间、链路等核心指标
- `AdminDashboardResourceView`：技能、工具、专家、MCP、意图、映射、示例问题等资产指标
- `AdminDashboardPerformanceView`：成功率、失败率、运行中占比、平均响应、P95 响应
- `AdminDashboardTrendBucketView`：时间分桶趋势数据

## 测试与验证方式

- 后端：`cd backend && mvn compile && mvn test`
- 前端：`cd frontend/admin && npm run build && npm run test:run`
- 浏览器：通过 `/web-access + CDP` 打开 `http://localhost:5003/`，验证亮色/暗色下的图表、文本和滚动条可读性，并输出截图到 `logs/`
