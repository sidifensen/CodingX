<p align="center">
  <a href="https://github.com/sidifensen/CodingX">
    <h1 align="center">CodingX</h1>
  </a>
</p>

<p align="center">
  <strong>面向开发者的 AI 工作台，把聊天、工具、自动化和本地工程上下文接到同一条链路里</strong>
</p>

<p align="center">
  <a href="https://github.com/sidifensen/CodingX/stargazers"><img alt="GitHub stars" src="https://img.shields.io/github/stars/sidifensen/CodingX?style=flat-square&logo=github" /></a>&nbsp;
  <a href="https://github.com/sidifensen/CodingX/network/members"><img alt="GitHub forks" src="https://img.shields.io/github/forks/sidifensen/CodingX?style=flat-square&logo=github" /></a>&nbsp;
  <a href="https://github.com/sidifensen/CodingX/commits/main"><img alt="Last commit" src="https://img.shields.io/github/last-commit/sidifensen/CodingX?style=flat-square" /></a>&nbsp;
  <img alt="Java" src="https://img.shields.io/badge/Java-21-007396?style=flat-square" />&nbsp;
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?style=flat-square&logo=springboot&logoColor=white" />&nbsp;
  <img alt="React" src="https://img.shields.io/badge/React-19-61DAFB?style=flat-square&logo=react&logoColor=111" />&nbsp;
  <img alt="Electron" src="https://img.shields.io/badge/Electron-31-47848F?style=flat-square&logo=electron&logoColor=white" />
</p>

## 什么是 CodingX？

CodingX 是一个围绕 AI 开发工作流设计的全栈项目。它不是单纯的聊天窗口，而是把模型路由、联网搜索、MCP 工具、本地代码执行、自动化任务、长期记忆、仓库规范上下文和桌面端能力组合到一起，让 AI 能在真实工程环境里完成更长链路的任务。

你可以把它理解成一个可本地部署的 AI 工作台：

- **用户端**负责聊天、工作空间绑定、工具过程展示、自动化任务和桌面通知偏好。
- **后端**负责编排模型、搜索、MCP、本地工具、自动化调度、权限策略、长期记忆和运行 trace。
- **管理端**负责治理中心、系统配置、会话观测、运行状态和资产管理。
- **桌面端**负责承载用户端页面，并为本地目录、系统通知和桌面宿主能力提供入口。

如果你想学习或验证一个“能真实跑起来”的 AI Agent 工程系统，CodingX 更关注这些问题：模型不稳定怎么办，工具循环怎么收口，本地文件怎么限制边界，后台任务如何交付，仓库规范和长期记忆如何进入上下文，管理端怎么观察系统状态。

## 快速导航

| 入口 | 说明 |
| --- | --- |
| [功能文档](docs/features/index.md) | 已实现能力的模块化说明 |
| [后端说明](backend/README.md) | Spring Boot 后端启动与分层说明 |
| [用户端说明](frontend/user/README.md) | 用户端 React 应用说明 |
| [管理端说明](frontend/admin/README.md) | 管理端 React 应用说明 |
| [桌面端说明](frontend/desktop/README.md) | Electron 宿主与打包说明 |
| [协作规范](AGENTS.md) | 提交、验证、注释、数据库和前端规范 |

<details>
<summary><b>目录</b>（点击展开）</summary>

- [什么是 CodingX？](#什么是-codingx)
- [快速导航](#快速导航)
- [为什么做这个项目](#为什么做这个项目)
- [核心能力](#核心能力)
  - [1. Agent Loop 与本地工具](#1-agent-loop-与本地工具)
  - [2. 联网搜索与 MCP](#2-联网搜索与-mcp)
  - [3. 工作空间、仓库规范与长期记忆](#3-工作空间仓库规范与长期记忆)
  - [4. 自动化任务](#4-自动化任务)
  - [5. 管理端治理中心](#5-管理端治理中心)
  - [6. 桌面端体验](#6-桌面端体验)
- [核心链路](#核心链路)
- [工程设计亮点](#工程设计亮点)
- [技术栈](#技术栈)
- [目录结构](#目录结构)
- [本地启动](#本地启动)
- [验证命令](#验证命令)
- [适合谁看](#适合谁看)

</details>

## 为什么做这个项目

很多 AI 应用 Demo 能完成“发一个问题、调一次模型、返回一段文本”，但真实开发工作流远比这复杂：

- 用户经常不是问一个独立问题，而是希望 AI 读上下文、查资料、跑命令、改文件、验证结果。
- 模型可能返回工具调用，工具执行后又需要把真实结果回灌给模型继续判断。
- 同一个项目里有 `AGENTS.md`、`CLAUDE.md`、团队规范和长期偏好，AI 不应该每次都从零开始。
- 后台任务可能几分钟后才结束，结果需要写回会话、点亮提醒、触发桌面通知。
- 管理员需要看到配置、权限、Hook、长期记忆、会话和运行状态，而不是只靠日志排查。

CodingX 就是围绕这些工程问题展开的。它关注的不是“如何包一层模型 API”，而是一次 AI 任务从用户输入、上下文组装、模型路由、工具执行、状态落库、前端反馈到后台治理的完整闭环。

## 核心能力

### 1. Agent Loop 与本地工具

CodingX 支持模型在聊天过程中发起本地工具调用，并进入“模型决策 -> 工具执行 -> 结果回灌 -> 模型继续决策”的循环。后端通过独立协调器控制工具轮次、重复调用和收口原因，避免工具循环散落在聊天服务里。

当前工具链覆盖命令执行、补丁写入、资源读取、图片读取、计划维护、目标状态维护和子代理状态工具。每次工具执行都会绑定用户、会话、运行 ID 和工作目录，路径越界会被拒绝，权限策略命中 `DENY` 或 `CONFIRM` 时也会阻止执行并写入审计。

### 2. 联网搜索与 MCP

普通问答、联网搜索和 MCP 工具不会被混成一条粗暴路径。聊天链路会做意图识别、问题拆分和上下文判断：需要实时信息时走搜索，需要业务工具时走 MCP，缺城市、时间等关键参数时先引导用户补全。

搜索结果会经过聚合、去重、重排和 TopK 截断。涉及“最新版本”“当前状态”“发布公告”这类时效问题时，系统会优先使用官方文档、变更日志和权威来源，降低过期资料误导回答的概率。

### 3. 工作空间、仓库规范与长期记忆

用户绑定本地工作空间后，后端会只读加载仓库内的规范文件，例如 `AGENTS.md`、`CLAUDE.md`、`GEMINI.md`、`.cursor/rules` 等，并按优先级、单文件长度和总长度做限制。这样模型在处理项目任务时能看到当前仓库的协作规范，而不是凭通用习惯行动。

长期记忆按用户和工作空间隔离，只有 ACTIVE 状态的记忆才会进入候选。仓库规范文件和长期记忆会一起组成 system prompt 片段：没有工作空间时不阻断聊天，没有记忆时也可以只注入规范文件。

### 4. 自动化任务

CodingX 支持在 `/automation` 页面创建、编辑、暂停、恢复和删除周期性 AI 任务，也支持在聊天中识别明确计划指令，例如“每天 18:00 帮我总结项目状态”。任务到期后由后端调度器认领，复用聊天执行链路生成结果。

手动创建的任务会自动创建或复用交付会话，聊天创建的任务会写回来源会话。任务完成后可以触发 Hook、桌面通知和会话未读提醒，用户离开页面后也能回到会话查看结果。

### 5. 管理端治理中心

管理端不是简单的 CRUD 后台，而是围绕 AI 工作流治理设计。它可以维护权限策略、Hook 规则、长期记忆和 Slash Command，也能查看权限审计、会话详情和系统运行状态。

治理链路让“AI 能做什么、什么时候需要确认、任务完成后触发什么动作、哪些仓库规范进入上下文”都有配置入口和审计记录，降低 Agent 类系统黑盒运行的风险。

### 6. 桌面端体验

Electron 桌面端默认承载用户端页面，开发态加载 `http://localhost:5002`，生产打包态可以加载内置 `user-dist`。桌面宿主预留本地能力桥接，并支持把后端 Hook 通知转成系统通知。

桌面端打包时会先构建用户端，再把用户端产物写入 Electron 应用资源。运行地址可以通过 `CODINGX_USER_URL` 和 `CODINGX_API_BASE_URL` 覆盖，方便本地开发和打包部署使用不同后端。

## 核心链路

一次本地代码类任务大致会经过这些步骤：

1. 用户在聊天页、桌面端或 CLI 发起请求，并带上当前会话、用户、工作空间和可选 Slash Command。
2. 后端保存用户消息，读取模型配置、搜索配置、MCP 配置、仓库规范文件和长期记忆，组装模型上下文。
3. 意图识别判断是否需要搜索、MCP、直接回答或本地工具；多子问题会拆分后分别路由。
4. 模型开始流式输出；如果返回工具调用，后端暂停展示过程文案，执行本地工具并把真实结果回灌为系统证据。
5. Agent Loop 协调器判断是否继续下一轮、是否达到轮次上限、是否重复调用或需要异常收口。
6. 模型生成最终回答后，后端落库消息、运行状态和 trace，前端展示正文、引用、工具过程和任务提醒。
7. 如果任务来自后台自动化，结果会写回来源会话或交付会话，并按 Hook 规则触发桌面通知或其他后续动作。

## 工程设计亮点

| 设计点 | 解决的问题 |
| --- | --- |
| 工具感知 Agent Loop | 支持模型多轮调用真实工具，同时用轮次上限和重复调用识别防止失控 |
| 工作目录边界 | 本地工具只能在绑定工作区内读写，降低误操作风险 |
| 权限策略与审计 | 命令、路径和工具编码可配置 ALLOW / CONFIRM / DENY，并记录决策 |
| 仓库规范上下文 | 自动读取项目规范文件，让模型遵守当前仓库约束 |
| 长期记忆 | 用户和工作空间级偏好可持久化，并按状态和作用域回注 |
| 搜索权威排序 | 对时效问题优先官方和发布来源，减少旧资料污染 |
| 自动化调度认领 | 使用到期快照条件更新，避免重叠扫描重复触发同一任务 |
| 后台交付会话 | 周期任务结果可以长期沉淀到会话，而不是只打一条日志 |
| Hook 通知链路 | 任务开始、失败、确认、完成等节点可触发后续动作 |
| 管理端治理 | 配置、策略、记忆、命令和审计集中管理，便于排查和运营 |

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Java 21, Spring Boot 3.4, MyBatis-Plus, Sa-Token, OkHttp, Redisson, Hutool |
| 数据 | PostgreSQL, Redis |
| 用户端 | React 19, TypeScript, Tailwind CSS 4, Vite+, Motion, react-markdown |
| 管理端 | React 19, TypeScript, Ant Design 5, AntV Plots, Tailwind CSS 4, Vite+ |
| 桌面端 | Electron 31, TypeScript, electron-builder |
| CLI | Java CLI / TUI |
| 测试 | JUnit 5, Mockito, Vitest, Testing Library, jsdom |

## 目录结构

```text
CodingX/
├── backend/             # Spring Boot 后端，按 interfaces/application/domain/infrastructure 分层
├── cli/                 # Java CLI / TUI 客户端
├── frontend/
│   ├── user/            # 用户端 React 应用
│   ├── admin/           # 管理端 React 应用
│   └── desktop/         # Electron 桌面宿主
├── docs/features/       # 功能文档
├── script/              # 辅助脚本
├── AGENTS.md            # 协作、提交、验证与工程规范
├── CLAUDE.md            # 兼容其他 Agent 的协作说明
└── README.md
```

后端依赖方向为 `interfaces -> application -> domain`，`infrastructure` 负责持久化和外部服务实现。协议层只做参数适配和响应封装，复杂业务决策下沉到 application service 或 domain service。

## 本地启动

### 环境要求

- JDK 21
- Maven 3.9+
- Node.js 20+
- npm
- PostgreSQL
- Redis

后端默认端口为 `5001`，用户端为 `5002`，管理端为 `5003`。

```bash
# 后端
cd backend
mvn spring-boot:run

# 用户端 http://localhost:5002
cd frontend/user
npm install
npm run dev

# 管理端 http://localhost:5003
cd frontend/admin
npm install
npm run dev

# 桌面端
cd frontend/desktop
npm install
npm run start
```

后端首次启动前，复制 `.env.example` 为 `.env`，并补齐数据库、Redis、对象存储和主密钥等基础设施配置。AI 模型、搜索和 MCP 等业务配置优先通过管理端写入数据库。

## 桌面端打包

```bash
cd frontend/desktop
npm install
npm run pack:win
```

打包产物默认输出到 `frontend/desktop/release/`。该目录是构建产物目录，不应提交到 Git。

## 验证命令

| 改动范围 | 命令 |
| --- | --- |
| 后端 | `cd backend && mvn compile && mvn test` |
| 用户端 | `cd frontend/user && npm run build && npm run test:run` |
| 管理端 | `cd frontend/admin && npm run build && npm run test:run` |
| 桌面端 | `cd frontend/desktop && npm run build && npm run test:run` |

## 适合谁看

<details>
<summary><b>展开查看</b></summary>

### 想学习 AI Agent 工程化的后端开发者

如果你已经熟悉 Java / Spring Boot，但不知道一个 Agent 系统如何落地到真实工程里，可以从 CodingX 看到模型路由、工具调用、权限策略、队列门控、调度任务、长期记忆和可观测链路如何组合。

### 想做 AI 开发工具的全栈开发者

CodingX 覆盖用户端、管理端、桌面端和 CLI。它不是只有后端服务，也不是只有前端聊天框，而是围绕“开发者工作台”把多个端侧体验串起来。

### 想研究 MCP、本地工具和自动化任务的开发者

项目里有 MCP 工具接入、本地命令执行、补丁应用、自动化调度和 Hook 通知，可以作为理解 AI 工具编排边界的参考。

</details>

## 进一步阅读

- [聊天 Agent Loop 运行时](docs/features/chat/agent-loop-runtime.md)
- [本地工具运行时](docs/features/chat/local-tool-runtime.md)
- [外部 MCP Server 运行时](docs/features/chat/mcp-external-server-runtime.md)
- [自动化定时任务](docs/features/automation/scheduled-tasks.md)
- [软件端治理工作台](docs/features/governance/governed-workbench.md)
- [仓库规范文件与长期记忆](docs/features/governance/repository-instruction-context-long-term-memory.md)

<p align="center">
  如果这个项目对你有帮助，欢迎 Star、Fork 或提交 Issue 一起完善。
</p>
