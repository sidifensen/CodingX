# CodingX 分期总览

更新时间：2026-05-12

## 一、总体顺序

项目按下面的顺序推进：

1. `Phase 1-3: Web`
2. `Phase 4-5: Skill / MCP`
3. `Phase 6-7: Electron`
4. `Phase 8-9: 云端环境`

这个顺序的目的很明确：

- 先把 Web 产品主结构做稳定
- 再把 Skill 和 MCP 变成正式产品模块
- 再给产品接入 Electron 和本地能力
- 最后再补云端执行环境

## 二、前端技术基线

前端统一采用下面这套技术栈：

- `React`
- `Vite+`
- `Tailwind CSS`

这套技术栈同时服务：

- Web 前端
- 后续 Electron 中复用的前端界面

## 三、后端技术基线

后端统一采用下面这套技术栈：

- `Spring Boot`
- `Sa-Token`
- `Lombok`
- `Hutool`
- `OkHttp`
- `MyBatis-Plus`
- `PostgreSQL`
- `Redis`

## 四、分期结构

### Phase 1：Web 页面骨架

先把 CodingX 的 Web 主界面和导航结构做出来。

### Phase 2：Web 任务主干

把任务模型、最小 API、任务列表和任务详情主链路做出来。

### Phase 3：Web 工作台闭环

把任务工作台、事件流、mock executor 和结果摘要做出来，收成 Web MVP。

### Phase 4：Skill 产品化

先把 Skill 做成正式模块，进入导航、页面和任务配置。

### Phase 5：MCP 产品化

再把 MCP 做成正式模块，并让任务和工作台能展示 MCP 绑定关系。

### Phase 6：Electron 宿主与本地资源

把现有 Web 前端搬进 Electron，并接入本地仓库和文件能力。

### Phase 7：Electron 本地执行与本地 MCP

把本地执行链路和本地 MCP 桥接补齐，让桌面端具备真实本地能力。

### Phase 8：云端执行基础

把执行目标抽象、执行路由、cloud workspace 和 cloud executor 做出来。

### Phase 9：云端回传与统一体验

把云端日志、产物、恢复，以及本地 / 云端统一体验收口。

## 五、每一期做什么

### Phase 1

先做 Web 页面骨架、导航和产品信息架构。

### Phase 2

做任务模型、最小 API 和 Web 任务主链路。

### Phase 3

做 Web 工作台、事件流、mock executor 和 Web MVP 收口。

### Phase 4

做 Skill 模块和任务 Skill 配置。

### Phase 5

做 MCP 模块和任务 MCP 配置。

### Phase 6

做 Electron 宿主与本地仓库 / 文件能力。

### Phase 7

做 Electron 本地执行与本地 MCP。

### Phase 8

做云端执行基础能力。

### Phase 9

做云端日志回传、恢复和本地 / 云端统一体验。

## 六、现在先做哪一期

现在先从 `Phase 1` 开始。

最先开工的不是整个 Web 一起上，而是先做两个最小切片：

1. `Phase 1：Web 页面骨架`
2. `Phase 2：Web 任务主干`

这两个切片做完，`Phase 3` 的工作台和事件流才能稳定接上，后面的 Skill / MCP 入口也不会漂。

## 七、目录索引

每一期都单独放在一个文件夹中，结构如下：

- `docs/project/plans/phase-1-web-shell/overview.md`
- `docs/project/plans/phase-2-web-task-core/overview.md`
- `docs/project/plans/phase-3-web-workbench/overview.md`
- `docs/project/plans/phase-4-skill-productization/overview.md`
- `docs/project/plans/phase-5-mcp-productization/overview.md`
- `docs/project/plans/phase-6-electron-shell/overview.md`
- `docs/project/plans/phase-7-electron-local-runtime/overview.md`
- `docs/project/plans/phase-8-cloud-runtime-foundation/overview.md`
- `docs/project/plans/phase-9-cloud-unification/overview.md`

阶段文档规范见：

- `docs/project/plans/phase-docs-spec.md`

每个阶段目录的标准文件为：

- `overview.md`
- `tasks.md`
- `acceptance.md`
- `progress-log.md`
- `decisions.md`
