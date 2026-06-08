---
type: decision
title: repository-memory-index
summary: 记录仓库已沉淀模块、契约和经验文档的入口
tags:
  - repository
  - memory
owned_paths:
  - frontend/user/src/views/chat
  - frontend/user/src/views/ChatView.tsx
  - backend/src/main/java/com/codingx/chat/application/service/chat
related_docs:
  - docs/superpowers/memory/desktop/electron-host-module-card.md
  - docs/superpowers/memory/desktop/electron-host-contract.md
  - docs/superpowers/memory/cli/codingx-cli-tui-module-card.md
  - docs/superpowers/memory/chat/message-process-timeline-module-card.md
  - docs/superpowers/memory/chat/message-process-timeline-contract.md
  - docs/superpowers/memory/chat/background-stream-resume-contract.md
  - docs/superpowers/memory/admin/workspace-management-module-card.md
  - docs/superpowers/memory/admin/workspace-management-contract.md
  - docs/superpowers/memory/lessons/stream-replay-stale-closure-overwrites-content.md
  - docs/superpowers/memory/lessons/chat-citation-links-must-not-wait-for-slow-replay-panels.md
  - docs/superpowers/memory/lessons/share-selection-must-ignore-unpersisted-message-ids.md
  - docs/superpowers/memory/lessons/ai-adapter-provider-leaks-to-audit-fields.md
  - docs/superpowers/memory/tool/codex-local-tool-runtime-module-card.md
  - docs/superpowers/memory/tool/codex-local-tool-runtime-contract.md
last_verified_commit: df295563eedea6ac3e6b6e500b3487c9c7b12db7
status: active
---

# Repository Memory

当前已覆盖的仓库记忆：

- `docs/superpowers/memory/cli/codingx-cli-tui-module-card.md`
  - Java CLI TUI-only 入口、tui4j 状态模型、mock AgentEvent 渲染和常见验证陷阱

- `docs/superpowers/memory/desktop/electron-host-module-card.md`
  - Electron 主进程、preload 桥接、本地能力上下文和当前权限状态边界
- `docs/superpowers/memory/desktop/electron-host-contract.md`
  - 桌面宿主 IPC、HostContext 与用户前端 host bridge 的消费契约

- `docs/superpowers/memory/chat/message-process-timeline-module-card.md`
  - 聊天主消息区过程时间线的职责边界与扩展点
- `docs/superpowers/memory/chat/message-process-timeline-contract.md`
  - 主消息区过程卡片与 SSE 事件映射契约
- `docs/superpowers/memory/chat/background-stream-resume-contract.md`
  - 聊天后台任务断开后继续执行、SSE 缓冲和前端续流恢复契约
- `docs/superpowers/memory/chat/web-search-module-card.md`
  - 聊天联网搜索运行时的通道、系统配置和后处理边界
- `docs/superpowers/memory/chat/web-search-runtime-contract.md`
  - 联网搜索从系统配置到标准来源候选的运行时契约
  - `docs/superpowers/memory/lessons/stream-replay-stale-closure-overwrites-content.md`
    - 流式收敛回放不能用旧闭包覆盖刚生成完的 assistant 正文
  - `docs/superpowers/memory/lessons/chat-citation-links-must-not-wait-for-slow-replay-panels.md`
    - 聊天正文里的引用链接回填不能跟步骤/产物等慢面板一起等待
  - `docs/superpowers/memory/lessons/default-cloud-history-must-not-include-local-workspaces.md`
    - 默认云端历史查询必须与本地工作空间历史分开，兼容旧的未归属会话
  - `docs/superpowers/memory/lessons/share-selection-must-ignore-unpersisted-message-ids.md`
    - 分享选择只能使用已落库的数值消息 ID，临时乐观消息必须在 UI 和提交前过滤
  - `docs/superpowers/memory/lessons/chat-task-reminder-read-state-must-use-local-snapshot.md`
    - 聊天任务完成提醒的已读状态必须写入本地快照，不能复用数据库置顶字段
  - `docs/superpowers/memory/lessons/electron-chat-bootstrap-must-be-idempotent.md`
    - Electron 聊天首屏初始化必须按认证态和工作区上下文做幂等保护
  - `docs/superpowers/memory/lessons/built-in-skill-database-records-must-match-classpath-manifests.md`
    - 内置技能写入数据库后，必须同步提供类路径 `SKILL.md`，否则运行时上下文无法读取
  - `docs/superpowers/memory/lessons/ai-adapter-provider-leaks-to-audit-fields.md`
    - AI 路由对外审计字段必须使用候选池 provider，不能泄漏内部协议适配器名
- `docs/superpowers/memory/chat/ai-routing-module-card.md`
  - 聊天 AI 模型路由、候选池和故障切换的职责边界
- `docs/superpowers/memory/chat/ai-routing-selection-contract.md`
  - 聊天模型候选选择的输入、输出和排序契约
- `docs/superpowers/memory/tool/codex-local-tool-runtime-module-card.md`
  - Codex 风格本地工具执行器的职责、入口与常见陷阱
- `docs/superpowers/memory/tool/codex-local-tool-runtime-contract.md`
  - 本地工具可见性、执行目录、输出和不可用状态契约
- `docs/superpowers/memory/governance/governance-workbench-module-card.md`
  - 软件端治理工作台的权限策略、Hook、项目画像、Slash Command 和聊天上下文注入边界
- `docs/superpowers/memory/governance/governance-workbench-contract.md`
  - 治理工作台管理端接口、项目画像表结构和后续长期记忆扩展契约
- `docs/superpowers/memory/admin/workspace-management-module-card.md`
  - 管理端工作空间只读管理页的职责边界、入口与常见陷阱
- `docs/superpowers/memory/admin/workspace-management-contract.md`
  - 管理端工作空间列表 API、分页、筛选与页面字段契约
- `docs/superpowers/memory/admin/dashboard-console-module-card.md`
  - 管理端 Dashboard 控制台的职责边界、布局承载与图表实现约束
- `docs/superpowers/memory/admin/dashboard-console-contract.md`
  - 管理端 Dashboard 聚合接口、时间窗口与趋势分桶契约
- `docs/superpowers/memory/admin/feedback-management-module-card.md`
  - 管理端反馈管理三页的职责边界、入口与常见陷阱
- `docs/superpowers/memory/admin/feedback-management-contract.md`
  - 管理端反馈列表、详情、引用来源 API 与前端消费契约

当前主要缺口：

- Java CLI 仍是 mock 事件源和基础 TUI 外壳，尚未接入真实 Agent API、MCP 工具状态和 TUI 内部会话状态机
- MCP / 思考统一事件模型仍以前端运行时聚合为主，未形成数据库级持久化协议
- Electron 本地权限仍是进程内确认状态，尚未接入后端策略、审计和管理端治理中心
- 搜索多 provider fallback、HTML 搜索源解析和 provider 级可观测性仍需按需求逐步沉淀
- 聊天页历史回放和本地快照的长期演化规则尚未独立成 runbook
- 模型 tool-call 流程与本地工具执行结果回灌仍需实现端到端契约
- 管理端 Dashboard 统计当前仍依赖应用层聚合，尚未沉淀大数据量场景的查询优化规范
- 项目画像和长期记忆仍处于治理工作台基础能力阶段，尚未形成可回注到 Agent 输入上下文的完整闭环
