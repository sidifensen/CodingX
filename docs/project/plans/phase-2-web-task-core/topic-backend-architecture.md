# Phase 2 后端架构设计

更新时间：2026-05-13

## 一、目标

在 `Phase 2` 先搭建一个可演进的后端主干，只覆盖 Web MVP 的最小闭环：

- 认证登录
- 任务创建与查询
- 工作空间最小建模
- 任务事件与产物存储
- AI 聊天会话与消息存储
- 为 `Phase 3` 的 `SSE`、mock runtime、流式 AI 回复预留稳定边界

## 二、工程形态

- 单仓库
- 单应用
- 单 `pom.xml`
- `Spring Boot` 单体后端
- 包级 DDD，而不是 Maven 多模块

目录原则：

```text
backend/src/main/java/com/codingx/backend
├─ common
├─ config
├─ auth
├─ task
├─ workspace
├─ event
├─ artifact
├─ runtime
└─ chat
```

各业务模块内部统一分层：

```text
<module>
├─ interfaces
├─ application
├─ domain
└─ infrastructure
```

## 三、技术栈约束

必须使用：

- `Spring Boot`
- `Sa-Token`
- `Lombok`
- `Hutool`
- `OkHttp`
- `MyBatis-Plus`
- `PostgreSQL`
- `Redis`

关键约束：

- 登录鉴权使用 `Sa-Token`
- 网络请求统一使用 `OkHttp`
- 工具辅助优先使用 `Hutool`
- 实时推送统一使用 `SSE`
- 环境变量加载参考 `D:\code\GraphHire\backend`

## 四、领域边界

### 1. auth

职责：

- 用户登录
- 当前用户上下文
- 基于 `Sa-Token` 的鉴权拦截

### 2. task

职责：

- 任务创建
- 任务列表
- 任务详情
- 任务状态流转

`Task` 作为核心聚合根，管理：

- 基本信息
- 状态
- 执行模式
- 关联 workspace
- 开始/结束时间
- 错误信息与摘要

### 3. workspace

职责：

- 任务执行上下文
- 仓库目录、分支、运行目标等最小描述

第一阶段不做复杂资源编排，只保留任务所需最小字段。

### 4. event

职责：

- 任务时间线
- 运行日志片段
- SSE 推送的数据载体

第一阶段作为任务附属记录存在，不做独立复杂聚合。

### 5. artifact

职责：

- 任务摘要
- 输出文本
- 文件路径或对象键

### 6. runtime

职责：

- 定义统一执行器接口
- 当前提供 `mock` 执行器
- 后续扩展到 `local / cloud`

### 7. chat

职责：

- AI 聊天会话
- 聊天消息持久化
- 通过 `OkHttp` 调用 AI 提供商
- 为流式输出提供事件桥接

## 五、数据模型

第一批表：

- `sys_user`
- `cx_workspace`
- `cx_task`
- `cx_task_event`
- `cx_task_artifact`
- `cx_chat_conversation`
- `cx_chat_message`

设计原则：

- 使用应用层保证一致性，不依赖复杂外键联动
- 审计字段统一包含创建时间、更新时间、软删除标记
- `event` 与 `chat_message` 保留时间线顺序字段

## 六、配置方案

参考 `GraphHire`：

- `backend/.env.example` 提供变量名模板
- 本地实际密钥存放在 `backend/.env`
- `application.yml` 使用 `spring.config.import` 读取 `.env`

关键变量：

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_PASSWORD`
- `SA_TOKEN_SIGN_KEY`
- `AI_PROVIDER`
- `AI_BASE_URL`
- `AI_API_KEY`
- `AI_CHAT_MODEL`
- `CORS_ALLOWED_ORIGINS`

## 七、接口范围

`Phase 2` 必做接口：

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `POST /api/tasks`
- `GET /api/tasks`
- `GET /api/tasks/{taskId}`
- `POST /api/chat/conversations`
- `GET /api/chat/conversations`
- `GET /api/chat/conversations/{conversationId}/messages`
- `POST /api/chat/conversations/{conversationId}/messages`

## 八、不做

当前阶段明确不做：

- 微服务拆分
- Maven 多模块
- Skill / MCP 正式业务模块
- 本地执行器与云端执行器
- WebSocket
- 复杂权限模型
