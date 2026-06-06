# MewCode 能力对标与 CodingX 后续路线图

## 文档用途

本文记录 2026-06-06 对 `MewCode Agent` 公开介绍页中 13 项能力的对标结果，用于后续多会话拆分开发任务。本文是路线图与缺口说明，不是已完成能力索引；未实现或部分实现的条目不应写入 `docs/features/index.md`，避免把规划能力误认为当前真实功能。

## 结论摘要

CodingX 已具备 Web / Electron 聊天工作区、Java CLI TUI、本地工具运行时、技能上下文、MCP 管理与管理端可观测基础。2026-06-06 已补齐 CLI Plan mode 透传、Claude Code 风格六大工具别名、Agent Loop 协调器、外部 MCP Server 运行时和 Skill Runtime 技能包元数据注入；`web-access` 技能的短句识别与搜索兜底也已修复。后半部分的 Hook、完整 Slash Command、真实 SubAgent、Git Worktree 并行隔离、Agent Teams 和长期记忆体系尚未完整实现。

后续应优先把 CodingX 做成“可观测、可治理、可配置的 AI 开发工作台”，不要简单复刻纯 CLI Coding Agent。更适合 CodingX 的差异化方向是：前端/桌面端可视化任务过程、管理端审计、权限策略中心、开发计划工作流、项目画像、运行复盘报告。

## 能力对标表

| MewCode 能力 | CodingX 当前状态 | 现有证据 | 后续建议 |
| --- | --- | --- | --- |
| 类 Claude Code 的终端交互体验 | 阶段完成 | `docs/features/agent/java-cli-terminal-mvp.md` 记录 Java CLI TUI 已接后端 `/api/chat/stream` SSE；当前 CLI 会把 Plan mode 透传到后端，后端在系统提示中注入规划模式约束。 | 继续补齐 CLI 会话恢复、权限审批、工具状态详情和更完整的键盘交互。 |
| 6 大核心编程工具 | 已完成基础对齐 | `docs/features/chat/local-tool-runtime.md` 记录 `ReadFile/WriteFile/EditFile/Bash/Glob/Grep` 已作为模型可见别名映射到 `read/write/edit/bash/find/grep`，并保留 `shell_command/exec_command/apply_patch`。 | 继续统一工具结果格式、错误兜底和前端工具卡片展示。 |
| 自主任务循环 Agent Loop | 阶段完成 | 后端已有模型 `tool_call -> 执行工具 -> 工具结果回灌 -> 继续生成` 的循环；`AgentLoopCoordinator` 统一处理轮次上限、重复调用和收口原因。 | 增加明确的任务计划、完成判断、失败重试、步骤状态和用户可见 checklist。 |
| MCP 协议接入 | 阶段完成 | `McpServerRuntimeService` 支持外部 MCP `tools/list` 发现、schema 缓存、健康状态写回和 `mcp__{mcpCode}__{toolName}` 远程 `tools/call`；内置天气/代码搜索执行器保持兼容。 | 继续补齐 stdio 执行隔离、鉴权配置、健康检查调度和管理端动态发现入口。 |
| Skill 技能包系统 | 阶段完成 | `SkillRuntimeService` 已解析 `SKILL.md`、`.codex-skill/skill.json`、`resources/`、`scripts/` 并注入运行时摘要；`web-access` 短句介绍和搜索兜底已修复。 | 继续补齐技能版本、依赖、启停策略、资源读取 UI 和更完整的运行时动态装载。 |
| Slash Command 命令框架 | 未完整实现 | 当前只有 `@skill` 前缀和技能选择，不等于 `/review`、`/commit`、用户自定义 slash command。 | 建议新增内置命令、用户自定义命令模板、命令参数解析、权限范围和执行历史。 |
| Hook 生命周期钩子 | 未实现 | 当前未看到工具调用前后、会话开始结束、测试失败、任务完成等可配置 Hook 运行时。 | 建议新增 Hook 注册表、触发点、条件过滤、动作执行器、失败兜底和管理端配置页。 |
| 5 层纵深权限防御 | 部分完成 | 本地工具已有工作目录边界、越界路径拒绝、工具白名单、PowerShell 约束和补丁路径规范化。 | 增加危险命令识别、敏感目录策略、未知操作确认、审批记录、会话级权限模式和管理端策略中心。 |
| 上下文压缩 + Token 管理 | 部分完成 | 仓库存在 `TokenCounterService`、聊天摘要、运行上下文等基础，但未形成完整长上下文自动压缩与回注闭环。 | 建议实现 token 预算、压缩触发、保留/丢弃规则、压缩摘要回注、压缩质量回归测试。 |
| 跨会话记忆系统 | 部分完成 | 会话、消息、run、trace、技能/MCP/专家上下文会落库；本地还有 `docs/superpowers/memory` 仓库记忆文档。 | 建议区分用户级记忆、项目级记忆、会话短期记忆，增加自动提取、确认、检索注入和遗忘机制。 |
| SubAgent 子任务分发 | 模拟/调试级 | `docs/features/chat/local-tool-runtime.md` 说明 `spawn_agent/send_input/wait_agent` 等工具当前是后端进程内状态模拟，不启动真实外部 Codex 子进程。 | 建议先做只读调研型 SubAgent，再扩展到可写子任务；需要明确输出契约、超时、取消和错误汇总。 |
| Git Worktree 并行隔离 | 未实现 | 目前只有历史设计/样例痕迹，未看到真实多 Agent worktree 创建、绑定和清理链路。 | 建议与 SubAgent 联动实现：每个可写子任务独立 worktree，完成后汇总 diff，用户确认后合并。 |
| Agent Teams 多 Agent 团队 | 未实现 | 当前有专家、技能、MCP 等管理概念，但不是长期协作 Agent Team 运行时。 | 建议暂缓，等 SubAgent 和 Worktree 稳定后再做；先定义角色、任务路由、共享记忆和交接协议。 |

## 推荐开发顺序

1. **Plan / Checklist 开发模式**：当前 CLI Plan mode 已能传到后端，但 Web 端还缺少可见计划与逐步执行状态。下一步应让复杂任务先形成 checklist，再按步骤执行。
2. **Slash Command 命令框架**：提供 `/review`、`/fix-test`、`/commit`、`/generate-doc` 等快捷入口，降低常用开发任务启动成本。
3. **权限策略中心**：把当前工具边界升级为可配置策略，包括命令风险、目录风险、审批模式和审计记录。
4. **Hook 生命周期机制**：在任务开始、工具调用前后、测试失败、任务完成、提交前触发自动动作。
5. **项目代码索引与仓库画像**：扫描 workspace，生成模块地图、测试命令、关键入口、风险点，作为 Agent 输入上下文。
6. **上下文压缩与长期记忆**：围绕项目级记忆和用户级记忆做自动提取、确认、检索和回注。
7. **真实 SubAgent 与 Worktree 并行隔离**：先支持只读调研型子任务，再支持可写子任务和独立 worktree。
8. **Agent Teams**：在 SubAgent、Worktree、记忆和权限都稳定后再做长期团队编排。

## 后续多会话拆分建议

后续开启多个会话时，建议每个会话只领取一个边界清晰的任务，避免同时修改同一批核心文件。

| 会话主题 | 建议写入范围 | 备注 |
| --- | --- | --- |
| Slash Command | 后端命令解析服务、用户端输入框命令提示、命令执行文档 | 可先只做内置命令，不做用户自定义。 |
| 权限策略中心 | 本地工具执行服务、策略配置表、管理端策略页面 | 涉及数据库时必须同步迁移和 `schema.sql`。 |
| Hook 机制 | Hook 注册/执行服务、任务生命周期入口、管理端配置 | 先做后端触发与日志，再做前端配置。 |
| Plan / Checklist | 聊天应用服务、前端过程时间线、执行步骤落库 | 与现有 `chat_execution_step` 关系较密切。 |
| 项目画像 | 本地工具/仓库扫描服务、记忆文档或数据库存储 | 可先生成只读摘要，不自动改代码。 |
| SubAgent / Worktree | 子任务运行时、Git worktree 管理、任务汇总 | 写入面大，建议单独完整设计后再实现。 |

## 注意事项

- 当前工作区存在其他会话改动，后续开发必须遵守 `AGENTS.md` 的多会话协作规范：不修改、不格式化、不回滚无关改动。
- 本文只记录对标结论和路线图，不代表这些能力已经实现。
- 涉及新增功能、业务逻辑、接口、数据库、缓存或多模块联动时，必须按仓库要求补充 `docs/features/<module>/<feature>.md` 和必要验证。
- 涉及前端页面或交互时，必须支持亮色/暗色主题，并按浏览器验证要求提供 CDP 证据。
- 涉及后端 Java 文件时，必须补齐类、字段、依赖、核心方法和关键分支注释。
