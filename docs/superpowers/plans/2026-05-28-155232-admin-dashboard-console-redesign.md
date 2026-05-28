# 管理端 Dashboard 控制台改版 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把当前管理端首页重做成接近 `ragent` 风格的控制台，并用 AntV 图表承载当前仓库真实可用的运营指标。

**Architecture:** 后端扩展 Dashboard 聚合接口以支持窗口化 KPI、性能摘要和趋势分桶；前端重构 Layout 和 Dashboard 页面，拆成 hooks、视图模型和 AntV 图表组件，亮暗主题统一走管理端令牌。

**Tech Stack:** Spring Boot, MyBatis-Plus, Mockito, JUnit 5, React, TypeScript, React Router, Vitest, Testing Library, `@ant-design/plots`.

---

### Task 1: Dashboard 文档与失败测试

**Files:**
- Create: `docs/superpowers/memory/admin/dashboard-console-module-card.md`
- Create: `docs/superpowers/memory/admin/dashboard-console-contract.md`
- Create: `docs/superpowers/specs/2026-05-28-155232-admin-dashboard-console-redesign-design.md`
- Create: `docs/superpowers/acceptance/2026-05-28-155232-admin-dashboard-console-redesign-acceptance.md`
- Create: `docs/superpowers/plans/2026-05-28-155232-admin-dashboard-console-redesign.md`
- Modify: `backend/src/test/java/com/codingx/chat/interfaces/controller/AdminChatConversationControllerTest.java`
- Modify: `frontend/admin/tests/api/adminChatApi.test.ts`
- Modify: `frontend/admin/tests/pages/Dashboard.test.tsx`
- Modify: `frontend/admin/tests/components/Layout.test.tsx`

- [ ] **Step 1: 写 Dashboard 接口与页面红测**

为后端控制器测试补 `window` 参数和新的 Dashboard 响应结构；为前端 API/页面/布局测试补时间窗口、图表容器和顶部工具栏断言。

Run: `cd backend && mvn -Dtest=AdminChatConversationControllerTest test`

Run: `cd frontend/admin && npm run test:run -- adminChatApi.test.ts Dashboard.test.tsx Layout.test.tsx`

Expected: FAIL，因为当前 Dashboard 接口结构、AntV 图表容器和布局工具栏都不存在。

### Task 2: 后端 Dashboard 聚合接口

**Files:**
- Modify: `backend/src/main/java/com/codingx/admin/application/service/AdminChatDashboardService.java`
- Modify: `backend/src/main/java/com/codingx/admin/application/service/AdminChatDashboardView.java`
- Modify: `backend/src/main/java/com/codingx/admin/interfaces/controller/AdminChatDashboardController.java`
- Create: `backend/src/main/java/com/codingx/admin/application/service/AdminDashboardKpiView.java`
- Create: `backend/src/main/java/com/codingx/admin/application/service/AdminDashboardResourceView.java`
- Create: `backend/src/main/java/com/codingx/admin/application/service/AdminDashboardPerformanceView.java`
- Create: `backend/src/main/java/com/codingx/admin/application/service/AdminDashboardTrendBucketView.java`
- Test: `backend/src/test/java/com/codingx/chat/application/service/AdminChatDashboardServiceTest.java`
- Modify: `backend/src/test/java/com/codingx/chat/interfaces/controller/AdminChatConversationControllerTest.java`

- [ ] **Step 1: 写后端服务红测**

新增 `AdminChatDashboardServiceTest`，覆盖窗口 bucket 数量、成功率/P95 计算和空数据行为。

Run: `cd backend && mvn -Dtest=AdminChatDashboardServiceTest,AdminChatConversationControllerTest test`

Expected: FAIL，因为新的 Dashboard 聚合结构和窗口化逻辑尚未实现。

- [ ] **Step 2: 实现最小后端聚合**

扩展 `getDashboard(window)`，基于 conversation/message/trace/workspace 等真实表数据计算：

```java
var dashboard = adminChatDashboardService.getDashboard("24h");
dashboard.kpis().activeUserCount();
dashboard.performance().successRate();
dashboard.trendBuckets().size();
```

并更新控制器：

```java
@GetMapping
public ApiResponse<AdminChatDashboardView> getDashboard(
    @RequestParam(defaultValue = "24h") String window
) {
    return ApiResponse.success(adminChatDashboardService.getDashboard(window));
}
```

- [ ] **Step 3: 复跑后端测试**

Run: `cd backend && mvn -Dtest=AdminChatDashboardServiceTest,AdminChatConversationControllerTest test`

Expected: PASS。

### Task 3: 前端 Layout 与 Dashboard 重构

**Files:**
- Modify: `frontend/admin/package.json`
- Modify: `frontend/admin/package-lock.json`
- Modify: `frontend/admin/src/api/adminChatApi.ts`
- Modify: `frontend/admin/src/components/Layout.tsx`
- Modify: `frontend/admin/src/pages/Dashboard.tsx`
- Create: `frontend/admin/src/pages/dashboard/useAdminDashboard.ts`
- Create: `frontend/admin/src/pages/dashboard/dashboardViewModel.ts`
- Create: `frontend/admin/src/pages/dashboard/components/DashboardHeader.tsx`
- Create: `frontend/admin/src/pages/dashboard/components/DashboardKpiSection.tsx`
- Create: `frontend/admin/src/pages/dashboard/components/DashboardTrafficOverview.tsx`
- Create: `frontend/admin/src/pages/dashboard/components/DashboardTrendGrid.tsx`
- Create: `frontend/admin/src/pages/dashboard/components/DashboardSidebar.tsx`
- Create: `frontend/admin/src/pages/dashboard/components/DashboardChartEmpty.tsx`
- Modify: `frontend/admin/src/index.css`
- Modify: `frontend/admin/tests/api/adminChatApi.test.ts`
- Modify: `frontend/admin/tests/pages/Dashboard.test.tsx`
- Modify: `frontend/admin/tests/components/Layout.test.tsx`

- [ ] **Step 1: 安装 AntV 并补前端红测**

Run: `cd frontend/admin && npm install @ant-design/plots`

再补 Dashboard 与 Layout 红测，确认页面需要新的顶部工具栏、AntV 图表容器和时间窗口切换行为。

Run: `cd frontend/admin && npm run test:run -- adminChatApi.test.ts Dashboard.test.tsx Layout.test.tsx`

Expected: FAIL，因为 Layout 和 Dashboard 仍是旧结构。

- [ ] **Step 2: 实现前端最小控制台**

拆分数据 hook、视图模型和图表组件，`Dashboard.tsx` 只保留页面编排：

```tsx
export function Dashboard() {
  const dashboard = useAdminDashboard();
  return (
    <div className="admin-console-shell">
      <DashboardHeader {...dashboard.headerProps} />
      <div className="grid gap-5 xl:grid-cols-[1fr_320px]">
        <DashboardMain {...dashboard.mainProps} />
        <DashboardSidebar {...dashboard.sidebarProps} />
      </div>
    </div>
  );
}
```

Layout 同步改为深色侧边栏与顶部工具栏，同时保留：

```tsx
<main className="relative flex-1 min-h-0 w-full overflow-y-auto overflow-x-hidden">
```

- [ ] **Step 3: 复跑前端测试**

Run: `cd frontend/admin && npm run test:run -- adminChatApi.test.ts Dashboard.test.tsx Layout.test.tsx`

Expected: PASS。

### Task 4: 功能文档与最终验证

**Files:**
- Modify: `docs/features/index.md`
- Create: `docs/features/admin/admin-dashboard-console.md`

- [ ] **Step 1: 补功能文档**

记录控制台用途、入口、核心流程、关键文件和验证方式。

- [ ] **Step 2: 后端验证**

Run: `cd backend && mvn compile`

Run: `cd backend && mvn test`

Expected: PASS；若被无关脏改动阻塞，记录具体失败原因。

- [ ] **Step 3: 前端验证**

Run: `cd frontend/admin && npm run build`

Run: `cd frontend/admin && npm run test:run`

Expected: PASS；若被无关脏改动阻塞，记录具体失败原因。

- [ ] **Step 4: 浏览器验证**

按仓库要求先确认 `5001`、`5003` 端口可用，再启动后端和管理端前端。

Run: `cd backend && mvn spring-boot:run`

Run: `cd frontend/admin && npm run dev`

使用 `/web-access + CDP` 打开 `http://localhost:5003/`，验证亮色与暗色 Dashboard，并把截图保存到 `logs/`。

- [ ] **Step 5: 提交**

Stage 仅本次改动，并使用中文提交信息：

`feat(admin): 重做管理端控制台首页`
