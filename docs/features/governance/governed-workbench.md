# 软件端治理工作台

## 功能用途

软件端治理工作台用于把桌面端和聊天工作区中的开发任务能力纳入可配置、可审计的治理链路。当前实现覆盖 Plan/Checklist 步骤落库、内置 Slash Command、工具权限策略、Hook 审计、项目画像扫描、长期记忆和管理端治理中心；真实 SubAgent/Worktree 并行隔离、Agent Teams 不属于本次已实现范围。

## 使用入口

- 用户端聊天输入区：输入 `/` 打开 Slash Command 面板，可选择 `/review`、`/fix-test`、`/commit`、`/generate-doc` 等启用命令。
- 管理端：侧边栏进入「治理中心」，可维护权限策略、Hook 规则、项目画像、长期记忆和 Slash Command，并查看权限审计与 Hook 审计。
- 后端接口：用户端命令目录为 `GET /api/chat/slash-commands`；管理端治理接口集中在 `/api/admin/governance/**`。

## 核心流程

1. 用户在聊天输入区输入 `/` 后，前端从工作区状态读取后端返回的启用命令目录，并在输入区上方展示命令面板；选择命令后，命令写入 `selectedSlashCommand`，输入框只保留用户正文。发送时 `useChatWorkspace` 把命令序列化为 `messages` 查询参数中的 `slash_command`，其中 `command_type=builtin`，同时追加 `text` 片段承载用户问题。
2. 后端聊天提交服务解析结构化 `slash_command`，当命令类型为 `builtin` 时调用 `SlashCommandService` 校验命令是否存在且启用。校验通过后把命令模板和用户问题组合进模型上下文；命令未知或停用时抛出中文 `BusinessException`，由全局异常处理返回 `ApiResponse.message`。
3. 工具执行前，`ChatToolExecutionService` 从 `ChatToolExecutionContext` 读取用户、会话、运行和工作目录上下文，再调用 `PermissionPolicyService` 按工具编码、命令片段和路径片段匹配策略。`DENY` 和 `CONFIRM` 会写入审计并阻止执行，`ALLOW` 或未命中策略时继续执行工具。
4. 聊天应用服务在工具调用前后和任务完成时触发 `HookRuleService`；当前 Hook 动作只写入审计，不执行外部副作用，Hook 异常只记录日志，不中断聊天流。模型调用 `update_plan` 时，后端把每个计划步骤落入 `chat_execution_step`，并向前端发布 `step` 事件，支持软件端查看计划和执行状态。
5. 管理端治理中心加载策略、Hook、项目画像、长期记忆、Slash Command 和审计数据；编辑操作使用 Ant Design Modal，接口错误直接展示后端 `ApiResponse.message`。项目画像扫描接收工作空间 ID 和路径，由后端扫描 Maven、NPM、Vite 等仓库标记并保存摘要、技术栈、模块地图、测试命令、关键入口和风险点。

## 关键文件

- `backend/src/main/java/com/codingx/governance/`：治理领域模型、仓储、服务和管理端/用户端接口。
- `backend/src/main/resources/db/migration/V20260606_211000__create_governance_workbench_tables.sql`：治理表迁移脚本。
- `backend/src/main/resources/db/schema.sql`：治理表基线结构。
- `frontend/admin/src/pages/GovernanceCenterPage.tsx`：管理端治理中心页面，包含项目画像和长期记忆页签。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：用户端命令目录加载、命令选择状态和流请求序列化。
- `frontend/user/src/views/ChatView.tsx`：聊天输入区 Slash Command 面板和已选命令标签。

## 关键数据结构

- `governance_permission_policy`：权限策略，包含工具编码、命令片段、路径片段、动作和风险等级。
- `governance_permission_audit`：权限判定审计，记录工具输入、工作目录、命中策略、决策和中文消息。
- `governance_hook_rule` / `governance_hook_audit`：Hook 配置与生命周期触发审计。
- `governance_project_profile`：项目画像扫描结果，记录工作空间路径、技术栈、入口、验证命令、模块地图、风险点和 Agent 上下文。
- `governance_long_term_memory`：长期记忆候选和已启用记忆，记录用户/项目范围、确认状态、来源会话和检索关键词。
- `governance_slash_command`：内置 Slash Command 配置，用户端只展示启用命令。

## 测试与验证

- 后端定向测试覆盖治理表结构、权限策略、Hook、项目画像、工具执行拦截、命令解析和计划步骤落库。
- 管理端测试覆盖治理 API、治理中心页面加载和策略编辑弹窗。
- 用户端测试覆盖 Slash Command 目录接口、`/` 命令面板、已选命令标签和 builtin 结构化流请求。
- 前端视觉验证需通过浏览器打开管理端治理中心和用户端聊天输入区，分别保存截图或计算样式证据。
