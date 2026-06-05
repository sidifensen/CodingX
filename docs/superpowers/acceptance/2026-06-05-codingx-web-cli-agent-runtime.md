# Acceptance Criteria: CodingX Web 与 CLI 双入口 Agent Runtime

**Spec:** `docs/superpowers/specs/2026-06-05-154321-codingx-web-cli-agent-runtime-design.md`
**Date:** 2026-06-05
**Status:** Draft

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 系统提供产品无关的 `AgentSession` 创建入口，Web 和 CLI 都能以用户身份创建会话。 | API | 后端服务启动，用户已登录并持有有效 satoken，请求体包含 workspace 路径或运行目标。 | `POST /api/agent/sessions` 返回成功 `ApiResponse`，响应包含非空 `sessionId`、当前用户标识、workspace 信息、状态和创建时间。 |
| AC-002 | 同一个 `AgentSession` 同一时间只能存在一个运行中的 `AgentTurn`。 | API | 已存在一个 session，先提交一个仍处于运行中的 turn。 | 第二次 `POST /api/agent/sessions/{sessionId}/turns` 不启动新 turn，并返回中文错误消息或排队状态；数据库或内存态中不会出现两个同时运行的 turn。 |
| AC-003 | `AgentEvent` 必须具备统一 envelope 字段，供 Web 和 CLI 以同一协议消费。 | Logic | 构造任意事件类型并发布。 | 事件对象包含 `sessionId`、`turnId`、递增 `sequence`、`eventType`、`payload`、`createdAt`，且 `sequence` 在同一 session 内按发布顺序递增。 |
| AC-004 | 第一阶段支持 spec 中列出的核心事件类型。 | Logic | 读取后端事件类型枚举或事件类型注册表。 | 枚举或注册表包含 `session.started`、`turn.started`、`assistant.delta`、`thinking.delta`、`tool.started`、`tool.output.delta`、`tool.completed`、`command.started`、`command.output.delta`、`command.completed`、`file.diff`、`approval.requested`、`approval.resolved`、`artifact.created`、`reference.created`、`turn.completed`、`turn.interrupted`、`error`。 |
| AC-005 | `assistant.delta` 只承载用户可见正文增量，不混入工具前内部计划。 | Logic | 模拟一次包含工具调用的 Agent Loop，模型先输出工具前说明再发起工具调用。 | 持久化和流式事件中的 `assistant.delta` 不包含被识别为内部工具计划的文本，工具过程只通过 `tool.*` 或 `command.*` 事件表达。 |
| AC-006 | `command.*` 事件携带命令执行的关键审计字段。 | Logic | 模拟发布一次命令开始、输出和完成事件。 | `command.started` payload 包含命令、工作目录和开始时间；`command.output.delta` 包含输出流类型和输出增量；`command.completed` 包含退出码、耗时和最终状态。 |
| AC-007 | `approval.requested` payload 能让 CLI 和 Web 独立完成审批展示。 | Logic | 构造一个写文件或执行高风险命令的审批请求。 | payload 包含审批标识、风险等级、审批原因、操作摘要、默认策略、过期处理方式和目标操作类型。 |
| AC-008 | 审批结果通过统一输入入口回传，并生成 `approval.resolved` 事件。 | API | 已存在等待审批的 turn，并持有审批标识。 | `POST /api/agent/sessions/{sessionId}/approve` 提交通过或拒绝后返回成功，事件流中出现同一审批标识的 `approval.resolved`，payload 包含审批结果、审批人和审批时间。 |
| AC-009 | 取消运行中的 turn 会停止后续执行并发布中断事件。 | API | 已存在运行中的 turn，并已订阅 session 事件流。 | `POST /api/agent/sessions/{sessionId}/interrupt` 返回成功，事件流中出现 `turn.interrupted`，turn 状态变为 interrupted 或 canceled，后续不再执行新的工具调用。 |
| AC-010 | CLI 使用 Java 技术栈并作为可构建的工程存在。 | Logic | 检查仓库文件结构和构建配置。 | 存在 Java CLI 工程或 Maven 子模块，源码目标版本为 JDK 21，并声明或封装 tui4j、Java HttpClient、SnakeYAML、JUnit 的使用边界。 |
| AC-011 | CLI `login` 不把令牌写入项目仓库。 | API | 在任意 Git 工作区运行 CLI 登录命令并输入后端地址与 satoken。 | 登录配置写入用户主目录下的 CodingX CLI 配置位置，当前项目目录和 Git 工作区不新增包含 satoken 的文件。 |
| AC-012 | CLI 能创建或恢复当前目录对应的 AgentSession。 | API | CLI 已登录，当前目录为一个本地仓库。 | 运行 `codingx` 或 `codingx exec "..."` 后，CLI 向后端提交当前目录路径，后端返回 session；再次运行 `codingx resume` 能定位最近 session。 |
| AC-013 | CLI `exec` 能提交一轮任务并持续打印事件。 | API | CLI 已登录，后端 Agent API 可用，模型调用可用或测试桩可用。 | `codingx exec "分析这个项目"` 创建 turn，标准输出按事件顺序显示助手正文、工具状态或命令输出，turn 完成时进程退出码为 0。 |
| AC-014 | CLI 能显示错误事件并以非零退出码表达失败。 | API | 后端测试桩发布 `error` 事件并结束 turn。 | CLI 打印 `error` payload 中的中文错误消息，非交互 `exec` 模式以非零退出码退出。 |
| AC-015 | CLI 收到审批请求时会暂停并允许用户输入 `y` 或 `n`。 | API | 后端测试桩在事件流中发布 `approval.requested`。 | CLI 显示风险等级、原因和操作摘要；用户输入 `y` 会调用 approve 接口通过审批，输入 `n` 会调用 approve 接口拒绝审批。 |
| AC-016 | CLI 第一阶段不直接绕过后端调模型或执行 MCP 工具。 | Logic | 检查 CLI 代码和测试。 | CLI 任务执行路径只调用 CodingX Agent API；OpenAI SDK、Anthropic SDK、MCP SDK 若存在，仅位于可选适配层或未启用路径，默认 `exec` 不直接调用它们。 |
| AC-017 | 后端统一执行工具和权限检查，CLI 只消费事件并回传输入。 | Logic | 模拟一次 CLI 提交任务并触发工具调用。 | 工具调用发生在后端服务内，CLI 进程没有直接读写被操作文件或启动命令子进程，CLI 只接收 `tool.*`、`command.*`、`file.diff` 事件。 |
| AC-018 | 工作区边界默认拒绝工作区外写入。 | Logic | 以工作区路径创建 session，并模拟工具尝试写入工作区外文件。 | 后端拒绝该操作，发布 `error` 或 `approval.requested` 中的拒绝/风险事件，不产生工作区外文件写入。 |
| AC-019 | 删除文件和高风险命令默认需要审批。 | Logic | 模拟删除文件、修改 Git 状态或执行安装依赖命令。 | 后端在执行前发布 `approval.requested`；未收到通过审批前不会执行目标操作。 |
| AC-020 | 现有 Web 聊天入口不因 Agent API 第一阶段新增而被移除。 | API | 后端新增 Agent API 后，用户侧聊天相关 controller 仍在工程中。 | 现有 `/api/chat/stream` 或其兼容入口仍可编译并通过现有聊天 controller 单测；Agent API 新增不要求 Web 端立即迁移。 |
| AC-021 | 现有任务流或后台任务兼容入口不因第一阶段新增而被强制迁移。 | API | 项目仍包含既有任务流能力或其兼容适配。 | 既有任务流相关测试或兼容适配测试仍能通过；Agent 事件映射可以新增但不删除现有消费路径。 |
| AC-022 | Web 和 CLI 可以对同一 `AgentEvent` 做不同展示而不改变事件 payload。 | Logic | 使用同一组 `AgentEvent` 测试数据分别进入 Web 映射函数和 CLI 渲染函数。 | Web 映射生成卡片/时间线状态，CLI 渲染生成终端文本；两者不修改原始事件对象。 |
| AC-023 | `AgentItem` 作为历史回放权威来源，不只依赖实时事件缓存。 | Logic | turn 完成后清空或截断实时事件缓存，再查询 session 历史。 | 历史接口仍能返回用户消息、助手消息、工具调用摘要、命令结果、审批记录和错误记录中的已持久化 item。 |
| AC-024 | 事件日志有裁剪或补偿策略，避免长输出无限增长。 | Logic | 模拟大量 `command.output.delta` 或长文本事件。 | 系统按配置截断、摘要或归档事件日志；查询最近事件不会返回超过配置上限的无限 payload。 |
| AC-025 | Agent Controller 只做协议适配，业务编排下沉到 service 层。 | Logic | 静态检查新增 controller 或通过代码评审检查。 | Controller 只包含鉴权、请求对象接收、调用应用服务和响应封装；Agent Loop、审批决策、工具执行、路径判断不在 controller 中实现。 |
| AC-026 | 所有面向前端或 CLI 的 Agent API 异常使用统一中文 `ApiResponse.message`。 | API | 触发未登录、无权访问 session、参数非法和未知异常。 | HTTP 响应为统一 `ApiResponse` 结构，`message` 为中文可展示文案，未知异常不泄露堆栈或内部实现细节。 |
| AC-027 | 新增数据库结构时同步 migration 与 `schema.sql`，并补齐中文注释。 | Logic | 实施阶段选择新增 `agent_session`、`agent_turn`、`agent_item`、`agent_event`、`agent_approval` 表。 | `backend/src/main/resources/db/migration` 存在对应迁移脚本，`backend/src/main/resources/db/schema.sql` 同步结构，新增表和字段都有中文注释且注释末尾不加句号。 |
| AC-028 | Java CLI 的配置解析使用 SnakeYAML，并能读取后端地址和默认运行策略。 | Logic | 准备一份 CLI YAML 配置文件。 | CLI 配置加载后能得到 server URL、token 存储位置、默认审批策略和最近 session 信息；缺失可选字段时使用明确默认值。 |
| AC-029 | CLI 事件解析器能容错未知事件类型。 | Logic | 给 CLI 事件解析器输入一个合法 envelope 但 eventType 未注册的事件。 | CLI 不崩溃，记录或显示未知事件摘要，并继续处理后续已知事件。 |
| AC-030 | 第一阶段验证命令覆盖后端与 CLI 改动面。 | Logic | 实施完成后执行项目验证。 | 后端改动执行 `mvn compile` 和相关 `mvn test`；CLI 模块执行对应 Maven 测试；如改动 Web 页面或交互，补充前端测试和 CDP 证据。 |
