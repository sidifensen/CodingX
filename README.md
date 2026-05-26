# CodingX

CodingX 是一个包含后端服务、用户前端、管理端前端与 Electron 桌面宿主的多模块项目。后端基于 Spring Boot 构建，前端基于 React、Tailwind CSS 与 Vite+ 构建，桌面端通过 Electron 承载用户前端页面并提供本地能力桥接。

## 项目结构

```text
CodingX/
├── backend/           # Spring Boot 后端服务
├── frontend/
│   ├── user/          # 用户端 React 应用
│   ├── admin/         # 管理端 React 应用
│   └── desktop/       # Electron 桌面宿主
├── docs/              # 项目文档
├── script/            # 辅助脚本
├── AGENTS.md          # Codex/Agent 协作规范
└── CLAUDE.md          # Claude 协作规范
```

## 环境要求

- JDK 21
- Maven 3.9+
- Node.js 20+
- npm
- PostgreSQL
- Redis

## 本地启动

启动前请确认端口未被占用：

- 后端服务：`5001`
- 用户前端：`5002`
- 管理端前端：`5003`

### 后端服务

```bash
cd backend
mvn spring-boot:run
```

后端配置通常需要先复制 `.env.example` 为 `.env`，并按本地环境补充数据库、Redis 与 AI 服务相关配置。

### 用户前端

```bash
cd frontend/user
npm install
npm run dev
```

默认访问地址：`http://localhost:5002`

### 管理端前端

```bash
cd frontend/admin
npm install
npm run dev
```

默认访问地址：`http://localhost:5003`

### 桌面端

桌面端默认加载用户前端开发地址 `http://localhost:5002`。开发时建议先启动用户前端，再启动 Electron 宿主。

```bash
cd frontend/desktop
npm install
npm run start
```

如需指定桌面端加载地址，可在 `frontend/desktop/.env.development` 中配置：

```bash
CODINGX_USER_URL=http://localhost:5002
```

## 常用验证命令

### 后端

```bash
cd backend
mvn compile
mvn test
```

### 用户前端

```bash
cd frontend/user
npm run build
npm run test:run
```

### 管理端前端

```bash
cd frontend/admin
npm run build
npm run test:run
```

### 桌面端

```bash
cd frontend/desktop
npm run build
```

## 打包桌面端

```bash
cd frontend/desktop
npm run pack:win
```

打包产物位于 `frontend/desktop/release/`。打包过程会先构建桌面端与用户前端，并将用户前端构建产物写入 Electron 应用资源。

## 后端架构约定

后端采用 DDD 分层思路，核心依赖关系为：

```text
interfaces -> application -> domain
                    ^          ^
                    |          |
             infrastructure ----
```

- `interfaces`：处理 HTTP 请求与响应，只调用 `application`
- `application`：编排用例流程，调用 `domain` 模型与仓储接口
- `domain`：承载核心业务规则，不依赖外层技术实现
- `infrastructure`：实现持久化、外部服务、消息流等技术细节

## 开发规范

- 提交前请阅读并遵循根目录 `AGENTS.md` 中的协作、提交、验证与注释规范。
- 后端新增工具类能力时优先复用 Hutool，避免重复实现通用逻辑。
- 前端页面与核心交互组件需要同时适配亮色与暗色主题。
- 前端正式交互禁止使用浏览器原生 `alert`、`confirm`、`prompt`。
- 涉及数据库结构变更时，需要同步提交迁移脚本与 `backend/src/main/resources/db/schema.sql` 更新。

## 更多说明

- 后端详情：`backend/README.md`
- 用户前端详情：`frontend/user/README.md`
- 管理端前端详情：`frontend/admin/README.md`
- 桌面端详情：`frontend/desktop/README.md`
