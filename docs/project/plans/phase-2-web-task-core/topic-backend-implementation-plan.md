# Phase 2 后端实施计划

> **执行说明：** 当前仓库不是 git 仓库，本计划沿用 superpowers 的分解方式，但跳过 worktree 与 commit 步骤。步骤使用 checkbox 语法跟踪。

**目标：** 完成单 `pom.xml` 后端骨架、认证、任务主干、数据库脚本与 AI 聊天基础能力

**架构：** 单体 Spring Boot 应用，按业务模块分包；模块内采用 `interfaces/application/domain/infrastructure` 分层；通过 `Sa-Token`、`MyBatis-Plus`、`Redis`、`OkHttp` 组合落地最小闭环

**技术栈：** `Spring Boot`、`Sa-Token`、`Lombok`、`Hutool`、`OkHttp`、`MyBatis-Plus`、`PostgreSQL`、`Redis`

---

### 任务 1：基础工程与配置

**文件：**
- Create: `backend/pom.xml`
- Create: `backend/.gitignore`
- Create: `backend/.env.example`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/codingx/backend/CodingXApplication.java`
- Create: `backend/src/main/java/com/codingx/backend/config/*`

- [ ] 建立 Maven 工程与依赖清单
- [ ] 接入环境变量导入、统一响应、全局异常处理、MyBatis-Plus、Redis、OpenAPI、Sa-Token 基础配置
- [ ] 确认工程可启动且 `mvn test` 可运行

### 任务 2：认证模块

**文件：**
- Create: `backend/src/main/java/com/codingx/backend/auth/**`
- Modify: `backend/src/main/resources/db/schema.sql`
- Modify: `backend/src/main/resources/db/init.sql`
- Test: `backend/src/test/java/com/codingx/backend/auth/**`

- [ ] 先写登录、登出、当前用户测试
- [ ] 实现 `sys_user` 持久化、密码校验、`Sa-Token` 登录态
- [ ] 完成受保护接口的鉴权拦截

### 任务 3：任务主干模块

**文件：**
- Create: `backend/src/main/java/com/codingx/backend/task/**`
- Create: `backend/src/main/java/com/codingx/backend/workspace/**`
- Create: `backend/src/main/java/com/codingx/backend/event/**`
- Create: `backend/src/main/java/com/codingx/backend/artifact/**`
- Modify: `backend/src/main/resources/db/schema.sql`
- Test: `backend/src/test/java/com/codingx/backend/task/**`

- [ ] 先写任务状态流转与应用服务测试
- [ ] 实现任务、工作空间、事件、产物模型与仓储
- [ ] 实现 `POST /api/tasks`、`GET /api/tasks`、`GET /api/tasks/{taskId}`

### 任务 4：聊天基础模块

**文件：**
- Create: `backend/src/main/java/com/codingx/backend/chat/**`
- Modify: `backend/src/main/resources/db/schema.sql`
- Test: `backend/src/test/java/com/codingx/backend/chat/**`

- [ ] 先写聊天会话与消息应用服务测试
- [ ] 实现聊天会话、聊天消息仓储
- [ ] 建立 AI 网关接口与 `OkHttp` 客户端实现

### 任务 5：数据库初始化与运行说明

**文件：**
- Create: `backend/src/main/resources/db/schema.sql`
- Create: `backend/src/main/resources/db/init.sql`
- Create: `backend/README.md`

- [ ] 整理 PostgreSQL 建表脚本
- [ ] 整理演示账号与本地运行方式
- [ ] 记录 `.env` 变量说明与启动命令
