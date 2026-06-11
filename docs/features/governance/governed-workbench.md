# 软件端治理工作台

## 功能用途

软件端治理工作台用于把桌面端和聊天工作区中的开发任务能力纳入可配置、可追溯的管理链路。当前实现覆盖 Plan/Checklist 步骤落库、内置 Slash Command、工具权限策略、自动化 Hook 规则、仓库规范文件上下文、长期记忆和管理端治理中心；真实 SubAgent/Worktree 并行隔离、Agent Teams 不属于本次已实现范围。原本的仓库扫描画像表、管理端扫描接口和画像页签已下线，不再作为 Agent 上下文来源。

## 使用入口

- 用户端聊天输入区：输入 `/` 打开 Slash Command 面板，可选择 `/review`、`/fix-test`、`/commit`、`/generate-doc` 等启用命令。
- 用户端本地工作空间：聊天绑定的 `workspaceId` 会让后端在模型调用前只读加载仓库内规范文件，并叠加当前用户和工作空间的 ACTIVE 长期记忆。
- 用户端记忆管理页：侧栏进入「记忆管理」或访问 `/memories`，查看、编辑、启用、停用或删除长期记忆。
- 管理端：侧边栏进入「治理中心」，可维护权限策略、自动化 Hook 规则、长期记忆和 Slash Command，并查看权限审计。
- 后端接口：用户端命令目录为 `GET /api/chat/slash-commands`；用户端记忆接口为 `/api/chat/memories/**`；管理端治理接口集中在 `/api/admin/governance/**`。

## 核心流程

1. 用户在聊天输入区输入 `/` 后，前端从工作区状态读取后端返回的启用命令目录，并在输入区上方展示命令面板；选择命令后，命令写入 `selectedSlashCommand`，输入框只保留用户正文。发送时 `useChatWorkspace` 把命令序列化为 `messages` 查询参数中的 `slash_command`，其中 `command_type=builtin`，同时追加 `text` 片段承载用户问题。
2. 后端聊天提交服务解析结构化 `slash_command`，当命令类型为 `builtin` 时调用 `SlashCommandService` 校验命令是否存在且启用。校验通过后把命令模板和用户问题组合进模型上下文；命令未知或停用时抛出中文 `BusinessException`，由全局异常处理返回 `ApiResponse.message`。
3. `ChatApplicationService` 构造模型历史前调用 `GovernanceAgentContextService`。该服务先按当前用户和 `workspaceId` 解析用户拥有的本地工作目录，再由 `RepositoryInstructionContextService` 只读发现仓库规范文件；未绑定工作空间、目录不存在或没有命中文件时返回空片段，不阻断聊天。
4. 仓库规范文件发现按稳定优先级读取 `AGENTS.override.md`、`AGENTS.md`、`CLAUDE.local.md`、`CLAUDE.md`、`.claude/CLAUDE.md`、`GEMINI.md`、`QWEN.md`，并支持 Cursor、Windsurf、Cline、Roo、Continue、Junie、OpenHands、Kiro、Aider 等常见规则文件或规则目录。读取时明确排除 `.github/copilot-instructions.md`、`docs/superpowers/memory/**`、`.codingx/context.md` 和 `.codingx/rules.md`；成功加载时后端只打印一条汇总日志，包含用户、工作空间、目录、命中数量、注入数量、总字符数和文件摘要，不再逐文件打印正文预览。单文件或总内容截断、目录扫描失败、文件读取失败仍以 WARN 记录，便于排查真实异常。
5. `GovernanceAgentContextService` 再按当前用户、工作空间和本轮问题读取最多 6 条 ACTIVE 长期记忆，和仓库规范文件一起组成 system prompt 片段。长期记忆先做用户和工作空间范围过滤，当前作用域内的 ACTIVE 记忆默认可进入回注候选；正文包含和关键词命中只提升排序，避免用固定项目问句词表误判用户问题。长期记忆缺失时只注入仓库规范文件；仓库规范文件缺失时仍可单独注入长期记忆；两者都缺失时返回空文本。
6. 工具执行前，`ChatToolExecutionService` 从 `ChatToolExecutionContext` 读取用户、会话、运行和工作目录上下文，再调用 `PermissionPolicyService` 按工具编码、命令片段和路径片段匹配策略。`DENY` 和 `CONFIRM` 会写入审计并阻止执行，`ALLOW` 或未命中策略时继续执行工具。
7. 后台任务派发进入执行线程后触发 `BEFORE_TASK_START` Hook；任务外层失败时触发 `TASK_FAILED`，工具权限策略需要用户确认时触发 `TASK_CONFIRM_REQUIRED`，成功收口时触发 `TASK_COMPLETED`。`HookRuleService` 只按触发点和条件关键字返回启用规则，并写应用日志；规则动作如 `DESKTOP_NOTIFY`、`PET_EVENT`、`LOCAL_SCRIPT` 由后续桌面端或自动化执行器解释，匹配失败不会中断聊天流。

## 关键文件

- `backend/src/main/java/com/codingx/governance/application/service/RepositoryInstructionContextService.java`：按 workspace 只读发现、过滤、读取和渲染仓库规范文件上下文。
- `backend/src/main/java/com/codingx/governance/application/service/GovernanceAgentContextService.java`：组合仓库规范文件与 ACTIVE 长期记忆并回注聊天上下文。
- `backend/src/main/java/com/codingx/governance/application/service/LongTermMemoryService.java`：提取、去重、启用、停用和检索长期记忆。
- `backend/src/main/java/com/codingx/governance/`：治理领域模型、仓储、服务和管理端/用户端接口。
- `frontend/admin/src/pages/GovernanceCenterPage.tsx`：管理端治理中心页面，保留策略、Hook、长期记忆、Slash Command 和权限审计治理。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：用户端命令目录加载、命令选择状态和流请求序列化。
- `frontend/user/src/views/ChatView.tsx`：聊天输入区 Slash Command 面板和已选命令标签。

## 关键数据结构

- `governance_permission_policy`：权限策略，包含工具编码、命令片段、路径片段、动作和风险等级。
- `governance_permission_audit`：权限判定审计，记录工具输入、工作目录、命中策略、决策和中文消息。
- `governance_hook_rule`：自动化 Hook 规则配置，内置任务开始前、任务需要确认、任务失败和任务完成四类触发点；不再保存 Hook 审计表。
- `governance_long_term_memory`：长期记忆记录，保存明确授权后已生效或已停用的用户/项目记忆、来源会话和检索关键词。
- `governance_slash_command`：内置 Slash Command 配置，用户端只展示启用命令。
- 仓库规范文件不上表，运行时从绑定工作目录只读加载并受单文件 `12,000` 字符、总计 `24,000` 字符上限保护。

## 测试与验证

- 后端定向测试覆盖治理表结构、权限策略、Hook、仓库规范文件发现与排除、上下文截断日志、工具执行拦截、命令解析和计划步骤落库。
- 管理端测试覆盖治理 API、治理中心页面加载和策略编辑弹窗。
- 用户端测试覆盖 Slash Command 目录接口、`/` 命令面板、已选命令标签和 builtin 结构化流请求。
- 前端视觉验证需通过浏览器打开管理端治理中心和用户端聊天输入区，分别保存截图或计算样式证据。
