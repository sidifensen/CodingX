# CodingX

CodingX 是一个面向开发者的 AI 工作台，围绕智能聊天、MCP 工具、本地代码执行、工作空间上下文、自动化任务和桌面端体验，提供一套可观测、可配置、可扩展的研发助手系统。

## 项目定位

CodingX 不是单一聊天页面，而是一套完整的 AI 工作台：

- 用户端提供 AI 会话、联网搜索、工具调用过程展示、工作空间绑定和任务反馈。
- 后端负责编排模型、MCP、搜索、本地工具、会话状态、配置密钥和运行 trace。
- 管理端提供配置、资产、会话、运行状态和数据面板，方便排查与运营。
- Electron 桌面端承载用户端页面，并为本地目录和桌面运行场景预留宿主能力。

## 核心能力

### AI 聊天与工具编排

- 支持流式回答、深度思考、引用链接、工具调用过程和任务完成提醒。
- 支持多子问题拆分与意图路由，让搜索、MCP 工具和直接回答走不同链路。
- 支持 Codex 风格本地工具，包括命令执行、补丁写入、资源读取、计划维护和子代理状态工具。
- 支持工作目录边界校验，避免模型工具越权访问无关路径。

### 搜索与 MCP

- 联网搜索 provider 可配置，并支持结果聚合、去重、重排和 TopK 截断。
- 对最新版本、发布公告、官方文档等时效问题优先使用权威来源。
- 内置天气查询 MCP，缺少关键参数时先引导补全，避免无意义工具调用。
- 工具结果会进入下一轮模型上下文，前端同步展示 start / complete / error 状态。

### 工作空间与会话归档

- 支持云端会话、本地会话和绑定目录的工作空间会话。
- 会话、消息、任务、run、trace、技能、专家和 MCP 绑定落库，便于回放与审计。
- 前端按运行目标和工作空间路径分区缓存，并兼容旧快照迁移。

### 配置、密钥与可观测

- 运行时配置收敛到数据库 `setting` 表，管理端可调整 AI 路由、搜索、MCP 和引导策略。
- 敏感配置通过主密钥加密入库，接口只回显脱敏值。
- Redis / Redisson 用于聊天队列门控和多实例并发协调，未启用 Redis 时可回退到进程内控制。
- 管理端提供活跃用户、会话趋势、消息趋势、链路成功率、P95 响应和资产概览。

### 桌面端

- Electron 桌面宿主默认加载用户前端 `http://localhost:5002`。
- 生产打包时可把用户端构建产物作为 `extraResources` 注入桌面应用。
- 支持通过 `CODINGX_USER_URL` 和 `CODINGX_API_BASE_URL` 覆盖页面地址与后端地址。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Java 21, Spring Boot 3.4, MyBatis-Plus, Sa-Token, OkHttp, Redisson, Hutool |
| 数据 | PostgreSQL, Redis |
| 用户端 | React 19, TypeScript, Tailwind CSS 4, Vite+, Motion, react-markdown |
| 管理端 | React 19, TypeScript, Ant Design 5, AntV Plots, Tailwind CSS 4, Vite+ |
| 桌面端 | Electron 31, TypeScript, electron-builder |
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

后端依赖方向为 `interfaces -> application -> domain`，`infrastructure` 负责持久化和外部服务实现。

## 环境要求

- JDK 21
- Maven 3.9+
- Node.js 20+
- npm
- PostgreSQL
- Redis

## 本地启动

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

## 常用验证

| 改动范围 | 命令 |
| --- | --- |
| 后端 | `cd backend && mvn compile && mvn test` |
| 用户端 | `cd frontend/user && npm run build && npm run test:run` |
| 管理端 | `cd frontend/admin && npm run build && npm run test:run` |
| 桌面端 | `cd frontend/desktop && npm run build && npm run test:run` |

## 文档入口

- [功能文档索引](docs/features/index.md)
- [后端说明](backend/README.md)
- [用户端说明](frontend/user/README.md)
- [管理端说明](frontend/admin/README.md)
- [桌面端说明](frontend/desktop/README.md)
- [协作规范](AGENTS.md)
