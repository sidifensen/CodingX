# Phase 1 Platform Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建 CodingX 第一阶段最小可运行骨架，打通任务创建、任务列表、任务工作台、任务状态流、云端/本地执行目标占位、以及 WorkBuddy 风格基础导航。

**Architecture:** 第一阶段不追求完整智能能力，而是先建立平台骨架。前端先搭用户端 Web 的稳定信息架构，后端先搭 `task + workspace + event` 主干模型与最小执行路由，执行器先做 mock 或本地模拟版本，保证后续云端环境和桌面端能平滑接入。

**Tech Stack:** React、TypeScript、Java 25、Spring Boot、MyBatis-Plus、PostgreSQL、Redis、WebSocket/SSE

---

## 先说结论

你现在不应该一上来就同时做：

- 管理端
- Electron
- Local Executor
- 真正的 ReAct / Plan-and-Execute
- Skill 市场
- MCP 平台
- 团队模式
- 真云端容器平台

这样一定摊薄。

你现在最合理的开工顺序是：

1. 先做 `用户端 Web + 平台后端 + 任务/workspace骨架`
2. 再做 `最小云端执行模拟`
3. 再做 `WorkBuddy 风格一级导航和模式切换`
4. 再做 `桌面端 + 本地执行`
5. 再做 `技能 / 工具 / MCP / 预设智能体 / 团队`
6. 最后做 `管理端`

原因很简单：

- 没有任务骨架，后面所有能力都挂不住
- 没有 workspace 模型，Trae Solo 风格云端环境无从实现
- 没有稳定导航，WorkBuddy 风格平台壳无从成立
- 没有执行器边界，Codex 风格编码能力无从承载

## 第一阶段范围

第一阶段只做这些：

- 用户端 Web
- WorkBuddy 风格左侧导航骨架
- 代码开发 / 日常办公模式切换
- 任务创建
- 任务列表
- 任务工作台
- task / workspace / event / artifact 最小数据模型
- 最小执行路由
- mock cloud executor

第一阶段不做这些：

- 真实 Skill 安装
- 真实 MCP 管理
- 真实团队协同执行
- 完整专业编码工作台
- Desktop/Electron
- 管理端 Web

## 建议的仓库结构

第一阶段建议先把目录定清楚，避免后面返工。

### 用户端前端

**Files:**
- Create: `D:\code\CodingX\apps\user-web\`
- Create: `D:\code\CodingX\apps\user-web\src\app\`
- Create: `D:\code\CodingX\apps\user-web\src\features\`
- Create: `D:\code\CodingX\apps\user-web\src\shared\`

职责：

- `app/` 放路由、全局 layout、providers
- `features/` 按任务、模式、智能体、工具等模块拆
- `shared/` 放 UI 组件、hooks、types、api client

### 平台后端

**Files:**
- Create: `D:\code\CodingX\apps\platform-api\`
- Create: `D:\code\CodingX\apps\platform-api\src\main\java\`
- Create: `D:\code\CodingX\apps\platform-api\src\test\java\`

职责：

- `task` API
- `workspace` API
- `event stream`
- `execution routing`
- `mock cloud executor registration`

### 共享文档与模型

**Files:**
- Keep: `D:\code\CodingX\docs\superpowers\specs\...`

第一阶段代码不一定要做 monorepo 工具链极致优化，但目录责任必须先清楚。

### Task 1: 建立仓库骨架

**Files:**
- Create: `D:\code\CodingX\apps\user-web\README.md`
- Create: `D:\code\CodingX\apps\platform-api\README.md`
- Create: `D:\code\CodingX\README.md`

- [ ] **Step 1: 创建应用目录结构**

在 `D:\code\CodingX` 下创建：

```text
apps/
  user-web/
  platform-api/
docs/
```

- [ ] **Step 2: 写根 README**

写清三个目标：

- 用户端 Web
- 平台后端
- 后续 Desktop / Admin 不在第一阶段实现

- [ ] **Step 3: 提交**

```bash
git add D:\code\CodingX\README.md D:\code\CodingX\apps
git commit -m "chore: initialize platform foundation workspace"
```

### Task 2: 设计并落地最小前端信息架构

**Files:**
- Create: `D:\code\CodingX\apps\user-web\src\app\routes.tsx`
- Create: `D:\code\CodingX\apps\user-web\src\app\layout\AppShell.tsx`
- Create: `D:\code\CodingX\apps\user-web\src\features\navigation\Sidebar.tsx`
- Create: `D:\code\CodingX\apps\user-web\src\features\mode\ModeSwitch.tsx`
- Create: `D:\code\CodingX\apps\user-web\src\features\tasks\TaskListPage.tsx`
- Create: `D:\code\CodingX\apps\user-web\src\features\tasks\TaskWorkspacePage.tsx`

- [ ] **Step 1: 先写页面结构测试或最小断言**

至少验证：

- 有左侧导航
- 有模式切换
- 有任务列表区域
- 有任务工作台区域

- [ ] **Step 2: 搭 WorkBuddy 风格基础壳**

左侧至少先放：

- 新建任务
- 智能体 / 专家
- 技能
- 工具
- MCP
- 资料库
- 自动化
- 任务列表

- [ ] **Step 3: 放入双模式切换控件**

模式只做 UI 和状态，不做复杂逻辑：

- `coding`
- `office`

- [ ] **Step 4: 做任务列表页**

先使用 mock 数据，展示：

- task title
- status
- updated time

- [ ] **Step 5: 做任务工作台页**

先展示：

- goal
- status
- workspace id
- artifact list
- event timeline

- [ ] **Step 6: 手动验证**

运行前端后，确认：

- 能切换模式
- 能从任务列表进入任务页
- 页面壳稳定

- [ ] **Step 7: 提交**

```bash
git add D:\code\CodingX\apps\user-web
git commit -m "feat: add phase-1 user web shell and task workspace skeleton"
```

### Task 3: 落地后端最小领域模型

**Files:**
- Create: `D:\code\CodingX\apps\platform-api\src\main\java\...\task\Task.java`
- Create: `D:\code\CodingX\apps\platform-api\src\main\java\...\workspace\Workspace.java`
- Create: `D:\code\CodingX\apps\platform-api\src\main\java\...\event\TaskEvent.java`
- Create: `D:\code\CodingX\apps\platform-api\src\main\java\...\artifact\Artifact.java`
- Create: `D:\code\CodingX\apps\platform-api\src\test\java\...\TaskModelTest.java`

- [ ] **Step 1: 写最小模型测试**

验证：

- 创建 task 时必须携带 `taskId`
- task 与 `workspaceId` 一对一关联
- event 必须带 `taskId`

- [ ] **Step 2: 实现最小模型**

字段至少包括：

- `taskId`
- `workspaceId`
- `title`
- `goal`
- `status`
- `taskMode`
- `executionTarget`

- [ ] **Step 3: 运行测试**

只跑领域模型测试，确保通过。

- [ ] **Step 4: 提交**

```bash
git add D:\code\CodingX\apps\platform-api
git commit -m "feat: add minimal task workspace event domain models"
```

### Task 4: 提供任务创建与查询 API

**Files:**
- Create: `D:\code\CodingX\apps\platform-api\src\main\java\...\task\TaskController.java`
- Create: `D:\code\CodingX\apps\platform-api\src\main\java\...\task\TaskService.java`
- Create: `D:\code\CodingX\apps\platform-api\src\test\java\...\TaskControllerTest.java`

- [ ] **Step 1: 写 failing test**

覆盖：

- 创建任务成功
- 自动分配 workspaceId
- 查询任务列表成功
- 查询任务详情成功

- [ ] **Step 2: 实现最小内存版 service**

第一阶段可以先用内存存储或简单 repository stub，不必马上接全量数据库。

- [ ] **Step 3: 跑接口测试**

确认：

- `POST /tasks`
- `GET /tasks`
- `GET /tasks/{taskId}`

都能返回最小数据。

- [ ] **Step 4: 提交**

```bash
git add D:\code\CodingX\apps\platform-api
git commit -m "feat: add minimal task creation and query APIs"
```

### Task 5: 实现最小 workspace 生命周期

**Files:**
- Create: `D:\code\CodingX\apps\platform-api\src\main\java\...\workspace\WorkspaceService.java`
- Create: `D:\code\CodingX\apps\platform-api\src\main\java\...\workspace\WorkspaceController.java`
- Create: `D:\code\CodingX\apps\platform-api\src\test\java\...\WorkspaceServiceTest.java`

- [ ] **Step 1: 写 failing test**

覆盖：

- 创建 task 时自动创建 workspace
- workspace 默认状态为 `created`
- workspace 能返回文件列表占位结构

- [ ] **Step 2: 实现最小 workspace service**

先不做真实文件系统扫描，只做元数据和占位返回。

- [ ] **Step 3: 跑测试**

确认 task 与 workspace 绑定稳定。

- [ ] **Step 4: 提交**

```bash
git add D:\code\CodingX\apps\platform-api
git commit -m "feat: add minimal task-bound workspace lifecycle"
```

### Task 6: 实现最小事件流

**Files:**
- Create: `D:\code\CodingX\apps\platform-api\src\main\java\...\event\TaskEventStreamController.java`
- Create: `D:\code\CodingX\apps\platform-api\src\main\java\...\event\TaskEventPublisher.java`
- Create: `D:\code\CodingX\apps\platform-api\src\test\java\...\TaskEventStreamTest.java`

- [ ] **Step 1: 写 failing test**

覆盖：

- 任务创建后能产生 `queued`
- 模拟执行后能产生 `running`
- 完成后能产生 `completed`

- [ ] **Step 2: 实现最小事件推送**

第一阶段可选：

- SSE
- WebSocket

优先用你最熟悉的。

- [ ] **Step 3: 跑测试并手动验证**

确认前端能订阅到状态变化。

- [ ] **Step 4: 提交**

```bash
git add D:\code\CodingX\apps\platform-api
git commit -m "feat: add minimal task event streaming"
```

### Task 7: 实现 mock cloud executor

**Files:**
- Create: `D:\code\CodingX\apps\platform-api\src\main\java\...\execution\ExecutionRouter.java`
- Create: `D:\code\CodingX\apps\platform-api\src\main\java\...\execution\MockCloudExecutor.java`
- Create: `D:\code\CodingX\apps\platform-api\src\test\java\...\MockCloudExecutorTest.java`

- [ ] **Step 1: 写 failing test**

覆盖：

- 选 `cloud` 的任务会进入 mock cloud executor
- executor 会把任务状态从 `queued` 更新为 `running`
- executor 最终会写回 `completed`

- [ ] **Step 2: 实现最小 mock executor**

第一阶段只需：

- 睡眠模拟执行
- 生成一条结果摘要
- 生成一个 mock artifact

- [ ] **Step 3: 跑测试**

确认平台具备“伪云端执行”闭环。

- [ ] **Step 4: 提交**

```bash
git add D:\code\CodingX\apps\platform-api
git commit -m "feat: add mock cloud executor flow"
```

### Task 8: 打通前后端任务工作台

**Files:**
- Modify: `D:\code\CodingX\apps\user-web\src\features\tasks\TaskListPage.tsx`
- Modify: `D:\code\CodingX\apps\user-web\src\features\tasks\TaskWorkspacePage.tsx`
- Create: `D:\code\CodingX\apps\user-web\src\shared\api\tasks.ts`

- [ ] **Step 1: 把前端 mock 数据替换为 API 调用**

读取：

- task list
- task detail
- workspace summary

- [ ] **Step 2: 接入事件流**

让任务工作台实时显示：

- status
- latest event
- artifact placeholder

- [ ] **Step 3: 手动跑通**

验证：

- 创建任务
- 选 `cloud`
- 看到任务变成 `running`
- 最终看到 `completed`

- [ ] **Step 4: 提交**

```bash
git add D:\code\CodingX\apps\user-web
git commit -m "feat: connect user web to task APIs and mock cloud events"
```

### Task 9: 阶段一验收整理

**Files:**
- Create: `D:\code\CodingX\docs\phase-1-manual-checklist.md`

- [ ] **Step 1: 写手工验收清单**

覆盖：

- 双模式切换可见
- 左侧导航可见
- 能创建任务
- 任务有 workspaceId
- 任务能走 mock cloud
- 工作台能看到状态变化

- [ ] **Step 2: 完整跑一遍**

手动截图或记录结果。

- [ ] **Step 3: 提交**

```bash
git add D:\code\CodingX\docs\phase-1-manual-checklist.md
git commit -m "docs: add phase-1 manual verification checklist"
```

## 计划后的真实开发顺序

如果你现在就要开始做，我建议按这个开发顺序，不要跳：

1. 先定目录结构和技术栈
2. 先做用户端 Web 壳
3. 再做 task/workspace 最小模型
4. 再做 task API
5. 再做 mock cloud executor
6. 再把前后端打通
7. 跑通第一条完整链路
8. 之后再补 Electron
9. 再补 Local Executor
10. 再补 Skill / 工具 / MCP / 团队 / 管理端

## 为什么是这个顺序

因为你现在最缺的不是功能想法，而是第一条最小闭环。

第一条最小闭环应该是：

- 用户在 Web 创建任务
- 任务生成 workspace
- 任务进入 mock cloud executor
- 任务有状态流
- 前端工作台能看到结果

这条打通后，你的产品才真正开始“活”起来。
