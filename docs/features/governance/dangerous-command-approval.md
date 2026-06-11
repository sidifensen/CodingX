# 危险命令即时确认

## 功能用途

危险命令即时确认用于把管理端权限策略中的 `CONFIRM` 动作接入真实工具执行链路。命中 `CONFIRM` 的本地命令不会立即执行，后端会创建一次性审批请求并通过聊天 SSE 推送到桌面/用户端或 CLI；用户明确选择允许后，仅当前 requestId 对应的同一条工具调用可继续执行。该功能不创建 7 天、会话级或永久授权，云端普通聊天在不运行本地工具时不需要展示确认入口。

## 参考取舍

- Claude Code 采用工具权限模式与规则组合：`default`、`plan`、`acceptEdits`、`bypassPermissions`、`dontAsk` 等模式决定默认行为，同时保留 `alwaysAllowRules`、`alwaysDenyRules`、`alwaysAskRules`。即使进入 `bypassPermissions`，显式 ask 规则、工具自身交互要求和安全检查仍可要求用户确认，因此 CodingX 的 `DENY` 与 `CONFIRM` 策略也不能被普通客户端偏好直接绕过。
- Codex 采用审批策略与沙箱配置拆分：`AskForApproval` 区分 `untrusted`、`on-request`、`on-failure`、`never`、`granular`，`SandboxMode` 区分 `read-only`、`workspace-write`、`danger-full-access`。危险命令在交互模式下进入 prompt；非交互或全权限模式依赖沙箱/策略决定允许或拒绝。CodingX 当前选择先落地更适合管理端治理的策略动作 `ALLOW` / `DENY` / `CONFIRM`，并把允许结果限制为一次性 requestId。
- 当前真实实现默认等价于“危险命令确认模式”：命中 `CONFIRM` 时逐次确认。用户提到的“完全权限模式”更适合作为后续桌面端本机偏好设计，建议命名为“信任执行模式”；在未补齐管理端允许范围和审计提示前，不应让它自动绕过管理端 `CONFIRM` 策略。

## 使用入口

- 管理端治理中心：管理员在权限策略中配置工具编码、命令片段、路径片段、动作 `CONFIRM` 和风险等级。
- 用户端/桌面端聊天：当本地工具调用命中 `CONFIRM` 策略时，输入区上方显示危险命令确认卡片，只提供 `拒绝` 和 `允许执行` 两个按钮。
- CLI TUI：后端 SSE 下发 `approval` 事件后，终端 transcript 展示命令摘要和热键提示，按 `a` 允许本次执行，按 `d` 拒绝本次执行。
- 后端接口：客户端通过 `POST /api/chat/permission-approvals/{requestId}` 回传 `{ "decision": "ALLOW" }` 或 `{ "decision": "DENY" }`。

## 核心流程

1. 管理员保存权限策略后，`PermissionPolicyService` 在工具执行前按当前用户、工具编码、命令输入和工作目录匹配启用策略。命中 `DENY` 时沿用即时拒绝；命中 `ALLOW` 或未命中策略时继续执行；命中 `CONFIRM` 且没有可消费审批请求时，会创建一次性 `PermissionApprovalRequest` 并抛出确认异常。
2. `ChatApplicationService` 捕获确认异常后，通过 `ChatStreamPublisher.publishApprovalRequest` 推送 `approval` SSE 事件，事件载荷包含 `requestId`、命令、工作目录、策略编码、风险等级和摘要。随后服务等待 `PermissionApprovalService.awaitDecision` 返回用户决定；等待期间工具执行不会进入真实 executor。
3. 用户端 `useChatWorkspace` 监听 `approval` SSE 后写入 `pendingPermissionApproval` 状态，`ChatView` 在输入区上方渲染确认卡片。用户点击 `拒绝` 或 `允许执行` 后，前端调用 `ChatApi.resolvePermissionApproval` 提交决定；接口错误时优先展示后端 `ApiResponse.message`，成功后清除当前卡片。
4. CLI 的 `BackendChatEventMapper` 把后端 `approval` SSE 映射为 `APPROVAL_REQUESTED`，`TuiTranscriptRenderer` 渲染中文确认提示和 `a/d` 热键。`CodingXTuiModel` 保存当前 requestId，拦截单字符 `a/A` 与 `d/D`，再通过 `PermissionApprovalResolver` 回写后端；其他事件源不支持回写时会在终端提示中文错误。
5. 后端审批接口校验登录用户和 requestId 后，把请求状态改为 `ALLOWED` 或 `DENIED` 并唤醒等待中的聊天任务。允许时，`ChatApplicationService` 将 requestId 绑定到 `ChatToolExecutionContext`，只重试同一条工具调用一次；策略服务会校验用户、会话、run、工具、输入、工作目录和策略全部一致后才消费该请求。
6. 拒绝、超时、重复消费、用户不匹配或命令上下文不匹配时，后端以中文错误结束本次工具调用，并写入权限审计。成功消费后请求状态变为终态，旧 requestId 不能复用，因此不会形成长期授权或会话级放行。

## 关键文件

- `backend/src/main/java/com/codingx/governance/application/service/PermissionApprovalService.java`：维护一次性审批请求、等待用户决定、校验并消费允许请求。
- `backend/src/main/java/com/codingx/governance/application/service/PermissionPolicyService.java`：在 `CONFIRM` 策略命中时创建审批请求，在重试时校验 requestId 是否与当前工具调用完全一致。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：发布审批 SSE、等待决定、允许后重试工具调用，拒绝或超时时返回受控错误。
- `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatStreamController.java`：提供 `POST /api/chat/permission-approvals/{requestId}` 审批回写接口。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：消费 `approval` SSE、维护待确认状态并调用审批接口。
- `frontend/user/src/views/ChatView.tsx`：渲染输入区上方的危险命令确认卡片。
- `cli/src/main/java/com/codingx/cli/backend/BackendChatEventSource.java`：把 CLI 审批决定 POST 回后端。
- `cli/src/main/java/com/codingx/cli/tui/CodingXTuiModel.java`：保存当前 CLI 待审批 requestId 并处理 `a/d` 热键。

## 关键数据结构

- `PermissionApprovalRequest`：一次性审批快照，记录用户、会话、run、工具编码、命令输入、工作目录、策略 ID、风险等级、状态和用户决定。
- `PermissionApprovalDecision`：审批决定枚举，当前只允许 `ALLOW` 与 `DENY`。
- `PermissionApprovalStatus`：审批请求生命周期状态，区分待处理、允许、拒绝、超时和已消费。
- `PendingPermissionApproval`：用户端确认卡片状态，承载 requestId、命令、工作目录、策略和风险等级。
- `PermissionApprovalResolver`：CLI 事件源回写审批决定的最小接口，避免 TUI 直接依赖后端 HTTP 实现。

## 测试与验证

- 后端定向测试覆盖 `CONFIRM` 创建审批请求、允许后只放行同一条工具调用、拒绝后不执行工具、旧 requestId 不可复用。
- 用户端测试覆盖 `approval` SSE 后输入区上方渲染确认卡片，并校验只出现 `拒绝` 和 `允许执行` 两个动作。
- CLI 测试覆盖 `approval` SSE 映射、审批 POST 请求体和 TUI `a/d` 热键回写。
- 前端视觉验证需在用户端聊天页触发或模拟审批卡片，保存截图或关键样式证据到 `logs/`，日志目录不纳入提交。
