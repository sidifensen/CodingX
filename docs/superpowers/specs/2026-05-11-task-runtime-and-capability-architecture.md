# 任务运行时与能力协议设计

日期：2026-05-11

## 1. 文档目标

本文件用于承接主产品方案，并把实现前必须固定下来的四个骨架定义清楚：

- Task 数据模型
- Task 状态机
- Capability 能力协议
- Cloud / Local 执行协议

如果这四部分不先定下来，前端、后端、桌面端和本地执行器会很快演化成各自为政的实现。

同时，本架构必须覆盖以下已经明确的一等能力：

- 技能系统
- 内置工具系统
- MCP 系统
- 多个预设智能体
- 团队模式

## 2. 设计原则

### 2.1 任务优先

平台主语义单位是任务，不是消息。消息只是任务发起和补充上下文的一种方式。

### 2.2 状态显式

所有长任务都必须有明确状态、可观测步骤和事件流，不能靠“当前回复内容”隐式表达。

### 2.3 宿主解耦

运行时不依赖当前是 Web 还是桌面端。宿主差异只体现在能力暴露和执行目标选择。

### 2.4 执行目标独立

云端执行和本地执行是两种“执行目标”，不是两套任务模型。

### 2.5 模式独立于宿主

同一个平台需要同时支持两种显式模式：

- 日常办公模式
- 代码开发模式

这两个模式共享任务模型，但在默认工具策略、工作台信息密度、推荐模板、执行器工具链和验证机制上不同。

对于代码开发模式，官方 Codex 相关资料说明了一个重要方向：编码 Agent 不只是代码补全器，而是一个可独立执行工程任务的执行体。因此本平台在 coding 模式下也必须围绕“执行闭环”设计，而不是围绕“单次代码回答”设计。

对于日常办公模式，WorkBuddy 这类产品提供了另一条重要经验：模式切换不仅影响可用工具，也影响首页推荐任务、快捷标签和默认任务意图。

## 3. Task 数据模型

### 3.1 Task 的最小定义

一个 Task 应该至少包含以下字段：

- `taskId`
- `workspaceId`
- `title`
- `goal`
- `status`
- `agentProfile`
- `executionTarget`
- `capabilityRequirements`
- `inputAttachments`
- `planSnapshot`
- `currentStep`
- `resultSummary`
- `artifactRefs`
- `createdAt`
- `updatedAt`
- `startedAt`
- `finishedAt`

### 3.2 字段含义

`taskId`

- 任务唯一标识

`workspaceId`

- 任务所属工作区或用户空间

`title`

- 用户可读标题

`goal`

- 任务目标描述，是平台后续规划与执行的依据

`status`

- 当前任务状态

`agentProfile`

- 当前任务采用的智能体或技能策略

后续建议补充：

- `agentId`
- `teamId`
- `skillBindings[]`
- `toolBindings[]`
- `mcpBindings[]`

`executionTarget`

- 执行目标，当前建议只允许：
  - `cloud`
  - `local`

`capabilityRequirements`

- 任务声明需要哪些能力，例如本地文件、shell、网页搜索、浏览器自动化等

`inputAttachments`

- 用户上传文件、关联文档、选择的本地目录等输入材料

`planSnapshot`

- 当前任务最新规划快照，便于前端展示和恢复

`currentStep`

- 当前正在执行的步骤索引或步骤标识

`resultSummary`

- 任务完成后用于列表展示的结果摘要

`artifactRefs`

- 与任务关联的文档、表格、报告、代码包、截图等产物引用

### 3.3 推荐扩展字段

后续可以逐步加入：

- `priority`
- `labels`
- `approvalPolicy`
- `resumeToken`
- `failureReason`
- `retryCount`
- `memoryScope`
- `triggerSource`
- `taskMode`
- `repoContext`

其中建议补充：

`taskMode`

- `office`
- `coding`

`repoContext`

- 当任务为专业编码任务时，记录仓库根目录、分支、工作区等信息

对于编码任务，还建议额外记录：

- `reviewMode`
- `gitStatusSnapshot`
- `verificationPolicy`

## 4. Task 内部结构

一个完整任务不能只有顶层状态，还应包含可拆解的内部对象。

在团队模式下，任务内部还必须能表达角色分工、协作步骤和汇总结果。

### 4.1 Plan

Plan 用来表达系统当前的执行规划，建议包含：

- `planId`
- `taskId`
- `version`
- `summary`
- `steps[]`
- `createdAt`
- `updatedAt`

### 4.2 Step

Step 用来表达一个可执行步骤，建议包含：

- `stepId`
- `taskId`
- `planId`
- `title`
- `description`
- `status`
- `toolHints`
- `dependsOn[]`
- `evidenceRefs[]`
- `startedAt`
- `finishedAt`

### 4.3 Artifact

Artifact 用来表达任务产物，建议包含：

- `artifactId`
- `taskId`
- `type`
- `name`
- `uri`
- `preview`
- `createdAt`

### 4.4 Event

Event 用来表达任务过程中的实时变化，建议包含：

- `eventId`
- `taskId`
- `type`
- `timestamp`
- `source`
- `payload`

在专业编码模式下，还应重点支持以下事件内容：

- 命令开始执行
- 命令标准输出
- 命令标准错误
- 测试通过或失败
- 补丁生成
- 文件修改摘要
- Git 状态变化
- Code Review 评论生成
- PR 草稿生成

在多智能体或团队模式下，还需要支持：

- `agent_assigned`
- `team_step_assigned`
- `agent_handoff`
- `team_summary_generated`

## 5. Task 状态机

### 5.1 顶层状态

建议第一版固定为以下状态：

- `draft`
- `queued`
- `planning`
- `running`
- `waiting_for_user`
- `paused`
- `completed`
- `failed`
- `canceled`

### 5.2 状态含义

`draft`

- 用户还在编辑任务，还没有真正提交执行

`queued`

- 任务已提交，等待调度

`planning`

- Runtime 正在拆解任务、生成或修正计划

`running`

- 任务正在执行步骤、搜索资料、调用工具或生成结果

`waiting_for_user`

- 任务因缺少输入、审批、授权或确认而暂停，等待用户

`paused`

- 用户或系统主动暂停

`completed`

- 任务成功结束，并已产出结果

`failed`

- 任务执行失败，且当前未自动恢复

`canceled`

- 用户取消或系统终止任务

### 5.3 主要状态流转

推荐的主流转如下：

- `draft -> queued`
- `queued -> planning`
- `planning -> running`
- `planning -> failed`
- `running -> waiting_for_user`
- `running -> paused`
- `running -> completed`
- `running -> failed`
- `waiting_for_user -> running`
- `paused -> running`
- `queued -> canceled`
- `planning -> canceled`
- `running -> canceled`
- `waiting_for_user -> canceled`
- `paused -> canceled`

### 5.4 前端必须可见的状态变化

以下变化必须推送给前端，而不是等刷新页面时再读取：

- 进入规划
- 计划更新
- 步骤开始
- 步骤完成
- 任务等待用户
- 任务失败
- 任务完成
- 新产物生成

## 6. Capability 能力协议

### 6.1 为什么要单独做能力协议

Web 和桌面端之所以能共用一套前端，不是因为它们本质一样，而是因为前端不直接假设自己“在哪运行”。它只消费一份能力声明。

因此，能力协议是共享前端成立的基础。

### 6.2 最小能力集

建议前端启动后拿到一份类似这样的能力对象：

```json
{
  "hostType": "web",
  "executionTargets": ["cloud"],
  "capabilities": {
    "localFiles": false,
    "localFolderPicker": false,
    "shell": false,
    "browserAutomation": true,
    "desktopNotifications": false,
    "officeInterop": false,
    "localMcp": false
  }
}
```

桌面端则可能返回：

```json
{
  "hostType": "desktop",
  "executionTargets": ["cloud", "local"],
  "capabilities": {
    "localFiles": true,
    "localFolderPicker": true,
    "shell": true,
    "browserAutomation": true,
    "desktopNotifications": true,
    "officeInterop": true,
    "localMcp": true
  }
}
```

### 6.3 能力协议应覆盖的维度

至少覆盖：

- `hostType`
- `executionTargets`
- `toolCapabilities`
- `artifactCapabilities`
- `notificationCapabilities`
- `permissionState`
- `codingCapabilities`
- `skillCapabilities`
- `builtinToolCapabilities`
- `mcpCapabilities`
- `teamCapabilities`

对于代码开发模式，建议单独提供一组编码能力声明，例如：

- `repoMount`
- `gitRead`
- `gitWrite`
- `commandExec`
- `patchApply`
- `testRun`
- `skillLoad`
- `mcpConnect`
- `codeReview`
- `prDraft`
- `multiTaskParallel`

对于日常办公模式，建议单独提供一组办公能力声明，例如：

- `docProcess`
- `sheetAnalysis`
- `pptGenerate`
- `researchSearch`
- `productOps`
- `officeAutomation`

对于技能系统，建议补充：

- `skillInstall`
- `skillEnable`
- `skillSearch`
- `skillBind`

对于内置工具系统，建议补充：

- `toolEnable`
- `toolDisable`
- `toolPolicyBind`
- `toolScopeControl`

对于 MCP 系统，建议补充：

- `mcpInstall`
- `mcpConfig`
- `mcpSearch`
- `mcpBind`
- `mcpHealthCheck`

对于团队模式，建议补充：

- `teamRun`
- `multiAgentCoordination`
- `roleAssignment`

### 6.4 能力协议的用途

前端根据能力协议决定：

- 是否显示“本地/云端”切换
- 是否允许选择本地文件夹
- 是否展示运行命令能力
- 是否允许绑定本地 MCP 工具
- 是否提醒某任务只能在桌面端运行

## 7. Cloud / Local 执行协议

### 7.1 目标

执行协议用于统一 Runtime 与执行器之间的任务下发和结果回传方式。

Runtime 不应直接知道底层是在云主机跑，还是在用户电脑上跑。它只需要向执行层提交规范化任务。

### 7.2 任务下发对象

Runtime 向执行层下发的执行请求建议包含：

- `executionId`
- `taskId`
- `target`
- `agentProfile`
- `planFragment`
- `toolPolicy`
- `capabilityRequirements`
- `inputRefs`
- `deadline`
- `resumeContext`
- `workspaceContext`
- `agentAssignments`
- `teamPlan`
- `toolSelection`
- `mcpSelection`

其中：

`target`

- `cloud`
- `local`

`planFragment`

- 当前执行批次要完成的计划片段

`toolPolicy`

- 允许调用哪些工具、哪些工具需要审批、哪些工具禁用

`resumeContext`

- 用于任务恢复执行时带上必要上下文

`workspaceContext`

- 用于携带代码仓库、分支、工作目录、可用命令、Skill 与 MCP 上下文

对于编码模式，`workspaceContext` 还应支持：

- 已打开或重点关注文件
- 目标 PR / Issue / 提交上下文
- 代码审查模式
- 验证命令列表

`agentAssignments`

- 用于指定当前步骤由哪个预设智能体或团队角色负责执行

`teamPlan`

- 用于描述团队模式下多个角色的步骤分配与汇总策略

`toolSelection`

- 用于指定当前任务允许使用哪些内置工具、优先顺序和限制策略

`mcpSelection`

- 用于指定当前任务可用的 MCP 服务集合及其作用范围

### 7.3 执行器回传对象

执行器回传建议包含以下事件类型：

- `execution_started`
- `plan_updated`
- `step_started`
- `step_log`
- `step_completed`
- `artifact_created`
- `waiting_for_user`
- `execution_failed`
- `execution_completed`

每个事件至少包含：

- `eventId`
- `executionId`
- `taskId`
- `timestamp`
- `eventType`
- `payload`

### 7.4 本地执行器特有要求

本地执行器除通用协议外，还必须补充：

- 设备标识
- 连接状态
- 当前在线能力
- 权限状态
- 心跳

这样后端才能知道：

- 某个用户当前是否有可用本地环境
- 本地环境是否还在线
- 某个任务能否切到本地执行

对于专业编码模式，本地执行器还必须补充：

- 当前仓库上下文
- 当前分支信息
- Git 脏状态检测
- 可运行命令清单或命令发现能力
- Skill 目录与可加载 Skill 清单
- MCP 连接状态
- 当前代码审查支持状态
- PR / 补丁导出能力

对于技能、MCP 与团队模式，本地执行器还必须补充：

- 已安装技能清单
- 可用内置工具清单
- 可用 MCP 清单
- MCP 健康状态
- 当前团队执行支持状态

## 8. Electron 与 Local Executor 边界

### 8.1 Electron 宿主职责

Electron 应负责：

- 承载 React 页面
- 登录态桥接
- 文件选择器
- 系统通知
- 托盘与窗口管理
- 与本地执行器通信
- 向前端注入能力声明

### 8.2 Local Executor 职责

Local Executor 应负责：

- 命令执行
- 文件系统读写
- 浏览器自动化
- Office 与本地软件联动
- 本地 MCP 调用
- 回传日志、结果、错误和产物

在专业编码模式下，Local Executor 还应支持：

- 仓库扫描与索引
- Git 信息读取
- 补丁生成与应用
- 测试、lint、typecheck 执行
- Skill 加载与运行
- 仓库内 MCP 工具发现与连接
- 代码审查上下文收集
- PR 草稿材料生成

在技能、MCP 与团队模式下，Local Executor 还应支持：

- Skill 加载与卸载
- 内置工具执行能力暴露
- MCP 连接与断线重试
- 面向单智能体与团队执行的上下文隔离

### 8.3 为什么不能把执行逻辑全塞进 Electron

如果把所有执行逻辑直接写进 Electron 主进程，会带来几个问题：

- 后续难以独立升级执行器
- 崩溃恢复和重连困难
- 协议边界不清晰
- 将来若支持“网页 + 本地桥接”时复用性差

因此推荐：

- Electron 是桌面宿主
- Local Executor 是本地执行进程
- 二者通过本地桥接协议通信

## 9. 推荐的项目模块划分

建议仓库后续至少拆成以下模块：

- `frontend-app`
- `desktop-shell`
- `platform-api`
- `agent-runtime`
- `execution-router`
- `local-executor`
- `agent-registry`
- `team-registry`
- `skill-registry`
- `tool-registry`
- `mcp-registry`

每个模块分别对应：

- 统一前端
- Electron 壳
- 平台 API
- Agent 运行时
- 云端/本地执行路由
- 本地执行器

## 10. 当前阶段建议

在进入实现前，建议继续写出以下内容：

1. 任务与步骤的数据库表设计
2. 事件推送协议
3. 权限模型与本地授权流程
4. Electron 与 Local Executor 的进程通信方式

没有这些定义，后面即使开始搭前端或后端，也会反复返工。
