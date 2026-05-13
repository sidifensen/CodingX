# 阶段验收标准

更新时间：2026-05-13

## 验收项

- AC-201：后端工程可通过 `mvn test` 与 `mvn package`
  验收方式：`test`
  通过标准：`backend/` 在本机 Java 21 + Maven 3.8.8 环境下测试通过并能成功打包

- AC-202：后端采用单 `pom.xml` + 包级 DDD 结构
  验收方式：`manual`
  通过标准：`backend/src/main/java` 下存在 `auth`、`task`、`workspace`、`event`、`artifact`、`runtime`、`chat` 模块，模块内分为 `interfaces/application/domain/infrastructure`

- AC-203：环境变量方案可用
  验收方式：`manual`
  通过标准：`backend/.env.example` 提供数据库、Redis、Sa-Token、AI 配置变量；`application.yml` 通过 `.env` 或系统环境变量读取这些值

- AC-204：认证链路可用
  验收方式：`test`
  通过标准：登录接口返回 Sa-Token token；未登录访问受保护接口返回鉴权失败；已登录可获取当前用户信息

- AC-205：任务主干 API 可用
  验收方式：`test`
  通过标准：支持创建任务、查询任务列表、查询任务详情；任务数据可写入 PostgreSQL 脚本定义的表结构

- AC-206：数据库文件完整
  验收方式：`manual`
  通过标准：存在 `backend/src/main/resources/db/schema.sql` 与 `backend/src/main/resources/db/init.sql`，并包含认证、任务、工作空间、事件、产物、聊天相关表结构或初始化数据

- AC-207：AI 聊天基础能力可用
  验收方式：`test`
  通过标准：支持创建聊天会话、保存用户消息、调用 AI 网关生成回复，并保存助手消息
