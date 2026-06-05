# CodingX Web 与 CLI 双入口 Agent Runtime 设计

**日期**：2026-06-05
**状态**：已按用户确认的产品方向形成设计草案，等待评审后进入验收标准与实施计划。

## 背景

CodingX 当前已经具备 Web 聊天页、管理端、聊天 SSE、后台任务流、本地工具运行时、MCP、Skill、Expert、过程时间线和会话历史等能力。现有运行时更偏 Web 聊天产品：用户通过浏览器发起问题，后端以 `message`、`thinking`、`step`、`mcp-call`、`tool-call`、`finish` 等事件驱动前端展示。

用户希望新增独立 CLI 程序形态，参考 OpenAI Codex 与 MewCode 这类终端 Coding Agent，让开发者可以在仓库目录中直接执行任务、查看工具输出、审批高风险操作、取消或恢复任务。该目标不适合把 Web 和 CLI 做成两套业务系统，而应抽象一套产品无关的 Agent Runtime，由 Web 与 CLI 共享。

## 参考边界

- OpenAI Codex 可借鉴其 `core / protocol / tui / app-server` 分层、`Thread / Turn / Item / Event` 思路、富客户端与核心运行时之间的协议隔离。
- MewCode 可借鉴其独立 CLI Coding Agent 能力清单，包括 Agent Loop、文件工具、Bash、MCP、Skill、Slash Command、权限、上下文压缩、记忆、SubAgent 与 Worktree。
- CodingX 不直接 fork 或照搬任何实现。项目已有 Java 后端、Web 用户端、Web 管理端和工具运行时，应迁移的是分层思想、事件协议与产品形态，而不是替换技术栈。

## 设计目标

1. 支持 **Web 工作台** 与 **独立 CLI 程序** 两个产品入口，共享同一套 Agent Runtime、工具执行、安全策略和历史模型。
2. 将现有聊天流从“聊天页面专用事件”逐步提升为产品无关的 `AgentEvent`，使 Web 可以展示为卡片/时间线，CLI 可以展示为终端日志。
3. 为 CLI 提供本地仓库入口能力：登录、创建会话、发送任务、实时接收事件、审批、取消、恢复最近会话。
4. 保留现有 Web 聊天、管理端、MCP、Skill、Expert 和后台任务能力，采用适配器逐步迁移，避免一次性推倒 Controller 与数据库。
5. 把权限审批、工作区边界和危险操作拦截作为一等能力，避免 CLI 形态引入不受控的文件写入、删除、命令执行或越界访问。

## 非目标

- 第一阶段不实现完整桌面端 UI。
- 第一阶段不引入真实多 Agent 团队协作、长期记忆、复杂 Hook、Worktree 自动编排和跨机器远程执行。
- 不重写所有现有 Controller。`ChatStreamController`、`TaskStreamController` 等现有入口继续保留，后续通过应用服务适配到统一 Agent Runtime。
- 不把 OpenAI Codex 或 MewCode 的实现代码直接迁入项目。

## 产品形态

### Web 端

Web 端定位为可视化工作台，继续承载用户可见会话、历史回放、附件、分享、MCP/Skill/Expert 选择、管理端配置、运行观测和后台任务详情。Web 可以在现有聊天页基础上逐步升级为 Agent Console：同一条回复中显示正文、思考、工具调用、命令输出、文件 diff、审批状态和错误兜底。

### CLI 端

CLI 端定位为本地开发入口。用户在仓库目录执行 `codingx` 后，CLI 读取当前工作目录作为 workspace，向后端创建或恢复 `AgentSession`，并通过 WebSocket 或流式 HTTP 接收 `AgentEvent`。CLI 输出应接近终端 Coding Agent：实时打印助手增量、命令输出、工具状态、文件改动和审批提示。

建议第一版命令：

```bash
codingx login
codingx
codingx "帮我分析这个项目"
codingx exec "修复登录失败的问题"
codingx resume
codingx sessions
```

其中 `codingx` 进入交互 TUI，`codingx exec` 执行非交互任务，`codingx resume` 恢复最近会话。

## 总体架构

```text
Web Chat / Web Agent Console        CodingX CLI
        |                               |
 REST + SSE / WebSocket          WebSocket / JSON-RPC
        |                               |
        +--------- Agent API ----------+
                      |
              Agent Runtime
                      |
  Agent Loop / Tool Runtime / Approval / Workspace / History
                      |
   DB / File Workspace / MCP / Skill / Expert / Model Provider
```

核心原则：入口可以不同，运行时只能一套。Web 与 CLI 都不直接编排工具循环、审批规则或工作区边界，只向 Agent API 提交用户输入并消费事件。

## 核心领域模型

### AgentSession

表示一次长期工作会话。它绑定用户、运行目标、仓库路径、工作区、会话标题、创建时间、最近活动时间和当前状态。Web 历史会话可以逐步映射为 `AgentSession`，CLI 会话天然以仓库路径作为主要上下文。

### AgentTurn

表示用户发起的一轮任务。一个 `AgentSession` 可以包含多个 `AgentTurn`，同一 session 默认同一时间只允许一个运行中的 turn，避免多个任务同时写同一工作区造成冲突。

### AgentItem

表示会话中的持久化过程单元。典型类型包括用户消息、助手消息、思考片段、工具调用、命令执行、命令输出、文件 diff、审批请求、引用来源、产物和错误记录。`AgentItem` 用于历史回放和审计，不能只依赖实时事件。

### AgentEvent

表示实时推给 Web 或 CLI 的事件。事件应短小、可增量消费、可排序，并带有 `sessionId`、`turnId`、`sequence`、`eventType`、`payload` 和 `createdAt`。Web 和 CLI 可以对同一事件做不同呈现。

### AgentInput

表示入口传回运行时的用户输入。典型输入包括继续对话、审批通过、审批拒绝、补充信息、取消任务、写入交互式命令 stdin、终端 resize 等。

## 事件协议

第一阶段建议收敛为以下事件类型：

```text
session.started
turn.started
assistant.delta
thinking.delta
tool.started
tool.output.delta
tool.completed
command.started
command.output.delta
command.completed
file.diff
approval.requested
approval.resolved
artifact.created
reference.created
turn.completed
turn.interrupted
error
```

事件 payload 应遵循以下规则：

- `assistant.delta` 只承载用户可见正文增量，不混入工具前内部计划。
- `thinking.delta` 只承载项目允许公开展示的模型思考或过程说明，不伪造隐藏思维链。
- `tool.*` 表示结构化工具调用，适合 Web 展示为卡片，也适合 CLI 展示为状态行。
- `command.*` 表示真实命令执行，必须携带命令、工作目录、输出流类型、退出码和耗时。
- `file.diff` 表示待应用或已应用的文件变更，必须标明路径、变更类型和是否越界。
- `approval.requested` 必须包含审批原因、风险等级、操作摘要、默认策略和过期处理方式。
- `error` 面向前端的错误文案必须延续现有中文 `ApiResponse.message` 语义，内部细节只进入日志。

## API 设计

### REST 入口

```text
POST /api/agent/sessions
GET  /api/agent/sessions/{sessionId}
GET  /api/agent/sessions
POST /api/agent/sessions/{sessionId}/turns
POST /api/agent/sessions/{sessionId}/input
POST /api/agent/sessions/{sessionId}/approve
POST /api/agent/sessions/{sessionId}/interrupt
GET  /api/agent/sessions/{sessionId}/events
```

REST 用于创建会话、查询历史、提交 turn、审批、取消和事件补偿拉取。Web 端可以短期继续使用现有聊天 REST，CLI 登录与 session 查询也可复用 REST。

### 流式入口

第一阶段可用 `GET /api/agent/sessions/{sessionId}/events` 做 SSE。若 CLI 需要真正双向交互，应增加：

```text
WS /api/agent/sessions/{sessionId}/ws
```

WebSocket 消息可以采用 JSON-RPC 2.0 风格，便于后续支持 `turn/start`、`turn/interrupt`、`approval/resolve`、`command/write`、`command/resize` 等双向操作。Web 端是否迁移 WebSocket 可按阶段决定，不阻塞 CLI MVP。

## 后端分层

```text
interfaces/controller
  AgentSessionController
  AgentStreamController

application/service
  AgentRuntimeService
  AgentTurnService
  AgentEventService
  AgentApprovalService
  AgentWorkspaceService

domain/model
  AgentSession
  AgentTurn
  AgentItem
  AgentEvent
  AgentApproval

infrastructure
  SseAgentEventPublisher
  WebSocketAgentEventPublisher
  AgentPersistenceRepository
  ToolExecutionAdapter
  WorkspaceBoundaryGuard
```

Controller 只负责协议适配、鉴权入口、参数接收和响应封装。Agent Loop、工具编排、审批决策、历史持久化和工作区边界必须下沉到 service 或 domain 层。

现有 `ChatStreamController` 可继续作为旧入口，内部逐步调用 `AgentRuntimeService`。现有 `SseChatStreamPublisher` 可先增加适配器，把聊天事件转换成 `AgentEvent`，待 Web 端完成消费迁移后再收敛协议。

## CLI 设计

CLI 第一阶段可以用 TypeScript 或 Rust 实现。若追求快速对接现有前端/Node 生态，TypeScript 更快；若追求终端 TUI 性能、分发单文件和长期可维护性，Rust 更接近 OpenAI Codex 的路线。

建议 MVP 能力：

1. `codingx login`：保存后端地址与 satoken，令牌只存本机用户目录，避免写入项目仓库。
2. `codingx`：读取当前目录，创建或恢复 session，进入交互模式。
3. `codingx exec <task>`：非交互提交一轮任务，持续打印事件，任务结束后以退出码表达成功或失败。
4. `codingx resume`：恢复最近 session 并继续订阅事件。
5. 审批输入：收到 `approval.requested` 后打印风险摘要，用户输入 `y`、`n` 或自定义补充说明。

CLI 渲染策略：

- `assistant.delta` 直接追加输出。
- `tool.started` 显示单行状态。
- `command.output.delta` 使用代码块或前缀流式输出。
- `file.diff` 默认折叠摘要，用户可展开查看。
- `approval.requested` 暂停当前 turn，等待用户输入。
- `error` 打印中文错误信息，并保留本地调试日志路径。

## 安全策略

CLI 端必须内置工作区边界与审批规则：

```text
工作区内读文件：默认允许
工作区外读文件：默认拒绝，除非用户显式授权
工作区内写文件：展示 diff，可按策略审批
删除文件：必须审批
执行普通命令：按配置审批或允许
安装依赖、修改 Git、启动长进程：建议审批
访问网络：按运行环境配置审批
工作区外写入、危险系统命令：默认拒绝
```

后端必须记录审批事件、审批人、审批时间、审批结果和被审批操作摘要。审批只是业务许可，不能替代底层路径边界和命令风险校验。

## 数据持久化建议

第一阶段可以不立刻大规模迁移 `chat_conversation`。建议新增 Agent 表并通过适配器与旧聊天表并行：

```text
agent_session
agent_turn
agent_item
agent_event
agent_approval
```

`agent_event` 可以只保留最近事件或按策略裁剪；权威历史应以 `agent_item` 为准，避免事件日志过大导致回放成本失控。涉及数据库变更时必须按项目规范同步 migration 与 `schema.sql`，并补齐中文表注释和字段注释。

## 与现有模块关系

- `ChatStreamController`：短期保留，作为 Web 聊天兼容入口。
- `TaskStreamController`：短期保留，后台任务事件可逐步映射为 `AgentEvent`。
- 本地工具运行时：作为 `ToolExecutionAdapter` 的主要底层实现，继续执行 read/write/edit/bash/grep/find/ls 等工具。
- MCP/Skill/Expert：继续作为 Agent Runtime 的上下文与工具来源，不在 CLI 第一阶段重写管理能力。
- 前端聊天页：短期消费旧事件；中期通过适配层消费 `AgentEvent`；长期可演进为 Web Agent Console。

## 实施阶段

### 阶段一：Agent 协议与 CLI MVP

新增 `AgentEvent`、`AgentSession`、`AgentTurn` 的后端模型与最小接口。CLI 支持登录、创建 session、发送 turn、接收事件、取消任务和审批。现有 Web 聊天不迁移，只验证 CLI 能跑通一轮本地仓库任务。

### 阶段二：工具事件统一

把现有 `tool-call`、`mcp-call`、`step`、`thinking`、`message` 事件映射为统一 `AgentEvent`。CLI 展示真实工具状态和命令输出，Web 端可以继续使用旧字段，也可以在局部组件中试点新事件。

### 阶段三：历史回放与恢复

完善 `AgentItem` 持久化和 `resume` 能力。CLI 和 Web 都能恢复 session，按 item 回放历史，按 event 补偿运行中状态。处理刷新、断线重连、任务完成提醒和后台运行状态一致性。

### 阶段四：Web Agent Console

在 Web 端新增或改造 Agent Console，使用统一事件渲染正文、过程、命令、diff、审批和产物。管理端增加 session/turn/item 查询与运行观测。

### 阶段五：高级 Agent 能力

在基础协议稳定后，再引入上下文压缩、项目记忆、Slash Command、Hook、SubAgent、Worktree 隔离和多 Agent 协作。每项能力单独走设计、验收和实施计划。

## 风险与约束

- CLI 真实执行命令和文件写入会放大安全风险，必须先完成工作区边界和审批策略。
- Web 与 CLI 双端共享事件协议后，事件 payload 设计不稳定会影响两个产品，第一阶段要保持小而稳定。
- 现有聊天表与新增 Agent 表并行期间，需要明确历史来源和状态权威，避免同一会话出现两套不一致状态。
- 长输出、命令日志和 event 日志可能快速膨胀，需要在设计中保留截断、归档和摘要策略。
- 现有工作区有多会话协作要求，实施时必须避免把其他会话的无关改动纳入提交。

## 验证策略

- 后端单测覆盖 session 创建、turn 提交、事件发布、审批解析、取消任务和工作区边界。
- CLI 单测或集成测试覆盖登录配置读取、事件流解析、审批输入、非交互退出码和断线恢复。
- Web 端迁移阶段用 Vitest 覆盖事件到 UI 状态的转换。
- 涉及前端可视化或终端模拟页面时，必须按项目要求使用 CDP 保存截图或计算样式证据。
- 涉及数据库结构时执行 `mvn compile`、`mvn test`，并检查 migration 与 `schema.sql` 注释一致。

## 待评审问题

1. CLI 第一版使用 TypeScript 还是 Rust。
2. 第一阶段流式通道使用 SSE + REST 输入，还是直接实现 WebSocket / JSON-RPC。
3. 新增 Agent 表是否第一阶段落库，还是先通过现有聊天与任务表做兼容映射。
4. Web 端是先新增 Agent Console，还是先保持聊天页不变，仅供 CLI 使用新协议。
5. 审批策略默认是偏保守还是偏效率优先。
