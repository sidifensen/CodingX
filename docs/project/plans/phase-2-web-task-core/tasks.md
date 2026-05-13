# 阶段任务清单

更新时间：2026-05-13

## 任务分组

### A. 后端基础工程

- [ ] `doing` 初始化 `backend/` 单 `pom.xml` Spring Boot 工程（owner: self）
  交付结果：可启动的后端骨架，包含 `Spring Boot`、`Sa-Token`、`MyBatis-Plus`、`PostgreSQL`、`Redis`、`OkHttp`、`Hutool`
- [ ] `todo` 建立包级 DDD 目录结构（owner: self）
  交付结果：`auth`、`task`、`workspace`、`event`、`artifact`、`runtime`、`chat`、`common`、`config`
- [ ] `todo` 建立环境变量导入方案（owner: self）
  交付结果：`.env.example`、`.gitignore`、`application.yml` 通过 `spring.config.import` 读取 `.env`

### B. 认证与基础设施

- [ ] `todo` 接入 `Sa-Token` 登录鉴权（owner: self）
  交付结果：登录、登出、当前用户接口可用，受保护接口需要登录
- [ ] `todo` 接入统一异常、统一响应、基础日志配置（owner: self）
  交付结果：统一 API 响应体、基础错误码与全局异常处理
- [ ] `todo` 接入 `MyBatis-Plus`、`Redis`、OpenAPI（owner: self）
  交付结果：基础配置可用，便于后续模块接入

### C. 任务主干

- [ ] `todo` 落地 `task/workspace/event/artifact` 最小模型（owner: self）
  交付结果：领域模型、持久化对象、Repository 接口与实现齐备
- [ ] `todo` 完成数据库脚本（owner: self）
  交付结果：`schema.sql`、`init.sql`，覆盖认证、任务、工作空间、事件、产物、聊天表
- [ ] `todo` 实现最小任务 API（owner: self）
  交付结果：`POST /api/tasks`、`GET /api/tasks`、`GET /api/tasks/{taskId}`

### D. AI 聊天基础

- [ ] `todo` 建立 AI 配置与 `OkHttp` 客户端（owner: self）
  交付结果：AI 基础配置从环境变量读取，可切换提供商与模型
- [ ] `todo` 建立聊天会话与消息持久化（owner: self）
  交付结果：会话创建、消息列表与消息保存基础能力

## 任务状态说明

- `todo`：未开始
- `doing`：进行中
- `done`：已完成
