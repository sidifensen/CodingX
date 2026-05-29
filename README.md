# CodingX

CodingX 是一个面向开发者的 AI 工作台，把对话、联网搜索、MCP 工具、本地代码执行与可观测后台编排到同一条链路里。用户可以在浏览器或 Electron 桌面端发起会话，模型按需调用网页搜索、天气等 MCP、Codex 风格本地工具完成任务，全过程都可以在管理端复盘。

## 产品能力

### 智能聊天工作区

- **多子问题逐题路由**：一次输入里的多个诉求会被改写并拆分，每个子问题独立做意图识别，搜索、天气 MCP、直答各走各的链路，避免被整体问题误导到同一个工具上。
- **歧义引导**：当意图候选分数接近时先反问澄清，必要时调 LLM 二次确认，避免在系统或主题不明确时盲目执行搜索或工具。
- **流式过程时间线**：助手消息把正文、深度思考、网页搜索、工具调用按到达顺序穿插展示，连续搜索折叠为“已搜索网页 N 次”，连续命令折叠为“已运行 N 条命令”。
- **流式引用**：回答中的 `[R1]` `[R2]` 在引用事件到达后立即可点击，无需等历史回放。
- **任务完成提醒**：后台任务终态后会在侧栏会话上点亮提醒圆点，进入会话或点击提醒后自动落库已读。
- **重复提交防抖**：会话级提交锁配合 Redis 队列门控，避免同一问题被快速重复发送。

### 联网搜索

- 接入可配置的搜索 provider，结果聚合后做去重、重排、TopK 截断。
- 针对“最新”“当前”“版本”“发布”类问题启用权威时效排序，让官方文档、变更日志、发布公告优先于第三方未确认来源。
- 改写阶段只做术语归一化，不追加厂商或域名锚点，保持搜索意图原貌。

### MCP 工具

- 内置天气查询 MCP，缺城市时先要求用户补全，避免空查询污染上下文。
- MCP 工具结果以系统证据形式追加到下一轮模型输入，前端实时展示 start / complete / error 过程卡片。

### Codex 风格本地工具

聊天模型可以调用真实接入的本地工具完成代码任务：

- `shell_command` / `exec_command`：在绑定的工作目录执行命令（Windows 服务端默认使用 PowerShell）。
- `apply_patch`：以工作目录为边界写入补丁，自动规范化绝对路径，处理同名新增、缺失 `diff --git` 头、带时间戳的文件头等异常 diff。
- 资源读取、图片读取、计划/目标维护、权限申请、插件申请。
- 进程内子代理状态工具（`spawn_agent`、`send_input`、`wait_agent`、`close_agent` 等）用于验证模型工具编排和链路调试。

工作目录由 `ChatToolExecutionContext` 在请求级绑定，越界路径直接拒绝。

### 工作空间与会话归档

- 云端模式未选项目时归档到“云端历史记录”，本地模式未选目录归档到“本地历史记录”，绑定本地目录后归档到对应工作空间。
- 会话、消息、任务、run、trace、技能/专家/MCP 绑定全部落库，方便回放和审计。
- 前端按 `runtimeTarget + workspacePath` 分区缓存，路径做大小写、斜杠归一，旧缓存自动迁移合并。

### AI 模型路由

- Provider、endpoint、候选池、首包超时全部由数据库 `setting` 控制，`application.yml` 只保留最小静态骨架。
- 默认优先级：硅基流动 DeepSeek → 硅基流动千问视觉 → 百炼文本 → 百炼思考 → 百炼视觉。
- 图片附件请求会优先筛选 `supports_vision=true` 的候选。
- 首包等待 15 秒超时后取消底层 OkHttp Call，立刻切换到下一个候选，旧慢连接不会继续占用读流资源。
- 候选 ID 维度累计失败，达到阈值后熔断。

### 系统配置与密钥

- 业务运行时配置统一收敛到 `setting` 表，宿主环境只保留主密钥 `APP_CONFIG_MASTER_KEY` 和基础设施配置。
- 敏感配置使用主密钥加密入库，接口只回显脱敏值，管理端留空表示保持原值。
- 加密算法标识与密钥版本随配置一起存储，支持后续轮换。

### Redis 队列门控

- 多实例部署时通过 Redisson 信号量限制聊天执行并发，自动续租避免长会话被误释放。
- 未启用 Redis 时回退到进程内并发控制。
- Redis 客户端使用 `StringCodec`，`chat:queue:*` 在 Redis 管理工具中显示为可读字符串。

### 管理端控制台

- 首页聚合活跃用户、会话与消息趋势、链路成功率、P95 响应、资产概览（技能、工具、专家、MCP、意图、映射、示例问题）。
- 支持 24h / 7d / 30d 窗口切换，AntV 图表 + 健康侧栏 + 实时运行时快照。
- 系统配置页可直接编辑歧义引导、AI 路由、搜索、MCP 等运行时配置。

## 技术栈

### 后端（`backend/`）

- **运行时**：JDK 21、Spring Boot 3.4
- **Web 与编排**：Spring Web、Spring Validation、Spring AOP、Spring Actuator
- **持久化**：PostgreSQL + MyBatis-Plus 3.5；迁移脚本位于 `db/migration`，基线结构在 `db/schema.sql`
- **缓存与协调**：Spring Data Redis + Redisson 3.31（`StringCodec`，承载聊天队列门控信号量与等待集合）
- **鉴权**：Sa-Token 1.45（含 Redis Jackson 会话存储）、Spring Security Crypto 用于密钥派生
- **AI / HTTP 调用**：OkHttp 4.12 直连 DeepSeek、SiliconFlow、百炼等 OpenAI 兼容 endpoint，支持取消底层 Call
- **对象存储**：AWS SDK v2 S3 客户端（`apache-client`）
- **文档解析**：Apache PDFBox 3
- **工具集**：Hutool 5.8（统一工具类底座，禁止重复造轮子）、Lombok 1.18
- **接口文档**：Springdoc OpenAPI 2.8
- **测试**：Spring Boot Test、JUnit 5、Mockito

### 前端 - 用户端（`frontend/user/`）

- **框架**：React 19 + TypeScript 5.8
- **构建**：vite-plus（基于 `@voidzero-dev/vite-plus-core`）、esbuild、`@vitejs/plugin-react`
- **样式**：Tailwind CSS 4 + `@tailwindcss/vite`、Autoprefixer
- **UI 与动效**：Lucide React 图标、Motion 12 动效库
- **Markdown 渲染**：`react-markdown` + `remark-gfm`，承载助手消息正文与 `[R1]` 引用链接
- **测试**：Vitest（vite-plus 提供）、Testing Library、jsdom

### 前端 - 管理端（`frontend/admin/`）

- **框架**：React 19 + TypeScript 5.8、React Router 7
- **构建与样式**：与用户端一致（vite-plus、Tailwind 4）
- **图表**：`@ant-design/plots` 2.6，承载控制台首页趋势图与健康侧栏
- **样式工具**：`clsx`、`tailwind-merge`
- **测试**：Vitest、Testing Library、jsdom

### 桌面端（`frontend/desktop/`）

- **运行时**：Electron 31 + TypeScript 5.8
- **打包**：electron-builder 24，输出 NSIS Windows 安装包；用户前端构建产物以 `extraResources` 形式注入 `user-dist/`
- **配置**：dotenv 加载 `CODINGX_USER_URL` 等运行时变量

### 基础设施 / 工具

- **数据库**：PostgreSQL（业务、聊天、工作空间、配置、运行时 trace）
- **缓存与协调**：Redis（聊天队列门控、会话缓存、Sa-Token 会话）
- **配置加密**：`APP_CONFIG_MASTER_KEY` 主密钥 + 数据库 `setting` 表密文存储
- **CI / 验证**：`mvn compile` / `mvn test`、`npm run build` / `npm run test:run`，前端交互改动通过 `/web-access + CDP` 做截图证据验证

## 模块结构

```text
CodingX/
├── backend/           # Spring Boot 后端，DDD 分层
├── frontend/
│   ├── user/          # 用户端 React 应用
│   ├── admin/         # 管理端 React 应用
│   └── desktop/       # Electron 桌面宿主
├── docs/              # 项目与功能文档
├── script/            # 辅助脚本
├── AGENTS.md
└── CLAUDE.md
```

后端依赖关系：`interfaces -> application -> domain`，`infrastructure` 实现持久化与外部服务，外层依赖内层。

## 环境要求

- JDK 21
- Maven 3.9+
- Node.js 20+
- npm
- PostgreSQL
- Redis

## 本地启动

启动前请确认端口未被占用：后端 `5001`、用户前端 `5002`、管理端前端 `5003`。

```bash
# 后端
cd backend && mvn spring-boot:run

# 用户前端 → http://localhost:5002
cd frontend/user && npm install && npm run dev

# 管理端前端 → http://localhost:5003
cd frontend/admin && npm install && npm run dev

# 桌面端（默认加载用户前端 5002）
cd frontend/desktop && npm install && npm run start
```

后端首次启动前需复制 `.env.example` 为 `.env`，按本地环境补充数据库、Redis、对象存储等基础设施配置。AI / 搜索 / MCP 等业务配置通过管理端写入数据库即可，无需散落到 `.env`。

桌面端加载地址可通过 `frontend/desktop/.env.development` 的 `CODINGX_USER_URL` 覆盖。

## 验证命令

| 改动面 | 命令 |
|---|---|
| 仅后端 | `cd backend && mvn compile && mvn test` |
| 仅前端（用户端） | `cd frontend/user && npm run build && npm run test:run` |
| 仅前端（管理端） | `cd frontend/admin && npm run build && npm run test:run` |
| 桌面端构建 | `cd frontend/desktop && npm run build` |
| 桌面端打包 | `cd frontend/desktop && npm run pack:win` |

打包产物位于 `frontend/desktop/release/`，过程会先构建桌面端与用户前端，再把用户前端构建产物写入 Electron 应用资源。

## 进一步阅读

- 功能文档索引：`docs/features/index.md`
- 后端模块说明：`backend/README.md`
- 用户前端说明：`frontend/user/README.md`
- 管理端前端说明：`frontend/admin/README.md`
- 桌面端说明：`frontend/desktop/README.md`
- 协作、提交、注释与验证规范：`AGENTS.md` / `CLAUDE.md`
