# 本项目云端执行架构设计

日期：2026-05-12

## 1. 文档目标

本文件用于把当前已经确定的云端环境原则，进一步落到可实现的架构层。

重点回答以下问题：

- `task`、`workspace`、`executor` 三者是什么关系
- 云端任务是怎么被调度和执行的
- 每任务一个独立 workspace 要怎么实现
- 本地如何模拟云端执行
- 将来如何迁移到真云服务器

## 2. 架构结论

当前阶段推荐采用以下模型：

- 一个 `task`
- 对应一个 `workspace`
- 对应一个 `cloud execution session`

也就是说，云端执行的最小单位不是用户，也不是项目，而是任务。

具体到工程上，应理解为：

- `task` 是业务对象
- `workspace` 是任务的文件与上下文容器
- `executor` 是执行任务的运行单元

## 3. 核心组件

### 3.1 Platform API

负责：

- 创建任务
- 保存任务与 workspace 元数据
- 提供查询与控制接口
- 向前端推送状态与事件

这是前端的统一入口，不直接执行任务。

### 3.2 Agent Runtime

负责：

- ReAct 决策循环
- Plan-and-Execute 拆解
- 选择智能体、团队、技能
- 选择内置工具与 MCP
- 生成执行请求

Runtime 负责“决定做什么”，不负责“在哪跑”。

### 3.3 Execution Router

负责：

- 根据 `executionTarget` 选择：
  - `cloud`
  - `local`
- 把任务分发到对应执行器
- 跟踪执行会话

它是控制面和执行面的边界。

### 3.4 Workspace Manager

负责：

- 创建 task 对应的 workspace
- 初始化目录结构
- 写入输入材料
- 管理 workspace 生命周期
- 提供产物和文件的访问索引

### 3.5 Cloud Executor

负责：

- 接收云端执行任务
- 启动隔离环境
- 挂载任务 workspace
- 实际运行工具、命令、技能、MCP
- 回传日志、状态、产物

### 3.6 Artifact Service

负责：

- 保存最终产物
- 保存中间文件索引
- 提供任务产物查询

### 3.7 Event Stream

负责：

- 实时向前端推送：
  - 任务状态
  - 步骤变化
  - 日志片段
  - 产物生成
  - 待办提醒

## 4. 三个核心对象的关系

### 4.1 Task

Task 是用户视角的任务对象。

它包含：

- 目标
- 状态
- 模式
- 智能体/团队策略
- 输入材料
- 执行目标

### 4.2 Workspace

Workspace 是任务运行时的专属文件空间。

它包含：

- 输入文件
- 中间文件
- 输出产物
- 日志
- 上下文快照
- 可选的代码仓库副本

### 4.3 Executor

Executor 是实际运行任务的执行单元。

它可以是：

- 本地进程
- Docker 容器
- sandbox worker
- 远程 runtime

当前阶段推荐：

- 本地执行：`local-executor`
- 云端执行：`cloud-executor`

## 5. 推荐的关系模型

### 5.1 一任务一工作区

- 一个 `taskId`
- 对应一个 `workspaceId`

### 5.2 一次执行一会话

同一个任务可以多次执行或恢复，因此建议单独引入：

- `executionSessionId`

这样：

- task 是任务身份
- workspace 是文件空间身份
- execution session 是运行会话身份

### 5.3 一个会话绑定一个执行器实例

每次云端执行会话，都会绑定一个执行器实例：

- 一个容器
- 或一个 runtime session

## 6. 推荐目录结构

对于每个云端任务，workspace 可以先做成一个独立目录：

```text
/workspaces/{workspaceId}/
  input/
  context/
  repo/
  scratch/
  artifacts/
  logs/
  meta/
```

说明：

- `input/`
  用户输入文件、副本、引用资料

- `context/`
  任务上下文快照、计划快照、配置快照

- `repo/`
  代码开发模式下的仓库副本或工作目录

- `scratch/`
  中间计算文件、临时文件

- `artifacts/`
  最终产物及可供展示的中间产物

- `logs/`
  执行日志、命令日志、错误日志

- `meta/`
  workspace 元数据、状态摘要、回收标记

## 7. 云端执行主流程

推荐主流程如下：

1. 用户创建任务
2. Platform API 写入 task
3. Workspace Manager 创建 workspace
4. Agent Runtime 生成计划
5. Execution Router 判断走 `cloud`
6. Cloud Executor 接收执行请求
7. 启动隔离运行环境
8. 挂载该任务专属 workspace
9. 执行步骤并持续写入 logs/artifacts/context
10. Event Stream 向前端推送变化
11. 执行完成后回收运行环境
12. 保留 workspace 元数据与产物

## 8. 本地如何模拟云端执行

第一版即使没有云服务器，也完全可以在本地模拟。

### 8.1 本地模拟结构

在同一台机器上启动：

- `platform-api`
- `agent-runtime`
- `execution-router`
- `cloud-executor`
- `local-executor`

其中：

- `cloud-executor` 逻辑上代表远程云端执行器
- `local-executor` 逻辑上代表用户本地执行器

虽然都在本机，但平台层仍然按两种执行目标处理。

### 8.2 本地模拟的关键要求

即使都在本机，也要保持：

- 独立进程
- 独立 workspace 目录
- 独立日志
- 独立状态回传

不能因为“暂时都在一台机器上”就把云端和本地逻辑混在一起。

### 8.3 更推荐的本地模拟方式

如果本机支持 Docker，推荐：

- `cloud-executor` 为每个任务启动一个本地容器
- 容器挂载任务专属 workspace

这样未来迁移到真云环境时，迁移成本最低。

## 9. 执行环境模板

云端执行不应只有一个统一镜像。建议按模式和任务类型准备模板。

### 9.1 办公模板

支持：

- 文档处理
- 表格处理
- PPT 生成
- 资料整理
- 浏览器操作

### 9.2 编码模板

支持：

- Git
- 代码搜索
- 命令执行
- 测试
- lint
- typecheck
- 补丁生成

### 9.3 浏览器模板

支持：

- 浏览器自动化
- 截图
- 表单填写
- 网页数据提取

## 10. 生命周期与回收策略

### 10.1 执行环境生命周期

执行环境应尽量短生命周期：

- 创建
- 执行
- 回传
- 回收

### 10.2 Workspace 生命周期

Workspace 生命周期应比执行环境更长：

- 创建
- 运行
- 完成保留
- 延迟回收

### 10.3 为什么要分离

因为：

- 执行容器可以很快销毁
- 但任务产物、日志、恢复信息不能立刻丢

这也是 Trae Solo 风格 workspace 体验成立的前提。

## 11. 后续迁移到真云服务器

未来迁移时，前端和任务模型应保持不变，只替换部署位置。

### 11.1 当前本地模拟

- `cloud-executor` 跑在本机

### 11.2 后续真云部署

- `cloud-executor` 跑在远程机器或 Kubernetes 集群中

保持不变的部分：

- task 模型
- workspace 模型
- 执行协议
- 前端状态机
- 日志和产物回传机制

## 12. 当前阶段推荐实现

当前阶段建议按以下顺序落地：

1. 先实现 task 与 workspace 的一对一关系
2. 再实现本地模拟的 `cloud-executor`
3. 再实现每任务一个隔离目录
4. 如条件允许，再升级为每任务一个本地 Docker 容器

这样可以最早验证：

- 任务隔离
- 多任务并发
- 云端 / 本地切换
- workspace 可见性

## 13. 最终判断

本项目当前阶段的最佳云端执行架构是：

- Trae Solo 风格任务级 workspace
- 任务级隔离执行单元
- 控制层与执行层分离
- 支持本地先模拟云端，再平滑迁移到真云
