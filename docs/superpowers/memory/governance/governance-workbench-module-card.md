---
type: module_card
title: governance-workbench-module-card
summary: 记录软件端治理工作台现有职责边界，避免扩展项目画像、记忆和权限能力时误改聊天主链路。
tags:
  - governance
  - admin
  - chat
owned_paths:
  - backend/src/main/java/com/codingx/governance
  - frontend/admin/src/pages/GovernanceCenterPage.tsx
  - frontend/user/src/views/chat
related_docs:
  - docs/features/governance/governed-workbench.md
  - docs/superpowers/memory/governance/governance-workbench-contract.md
entrypoints:
  - backend/src/main/java/com/codingx/governance/interfaces/controller/AdminGovernanceController.java
  - backend/src/main/java/com/codingx/governance/interfaces/controller/SlashCommandController.java
  - backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java
last_verified_commit: d9b47d7f535e549625b336ac0dbfd2eeac94f574
status: active
---

# Governance Workbench Module Card

## Responsibilities

- 管理本地开发任务的治理能力：权限策略、Hook 审计、项目画像扫描、Slash Command 配置。
- 向管理端提供 `/api/admin/governance/**` 接口，管理端 `GovernanceCenterPage` 聚合配置、画像和审计。
- 向用户端聊天输入区提供启用的 Slash Command 目录，并把内置命令模板注入聊天提交链路。
- 在本地工具执行前通过 `PermissionPolicyService` 做策略判定，在工具调用和任务完成生命周期通过 `HookRuleService` 写审计。
- 当前项目画像由 `ProjectProfileService` 只读扫描本地目录，保存技术栈、入口和验证命令摘要。

## Entry Points

- `AdminGovernanceController`：管理端策略、Hook、项目画像、Slash Command 的协议入口。
- `SlashCommandController`：用户端可见命令目录入口。
- `ProjectProfileService.scanWorkspace`：项目画像扫描和持久化入口。
- `ChatApplicationService.buildAiHistory`：聊天模型上下文组装点，当前会注入系统意图、Plan mode、搜索证据、专家和技能上下文。
- `ChatWorkspaceBindingService`：用户端/Electron 绑定本地仓库目录并落到 workspace。

## Invariants

- Controller 只做协议适配，复杂筛选、默认值、校验和上下文组装必须下沉到 service 层。
- 前端错误展示必须消费后端 `ApiResponse.message`，不要在页面层改写业务错误语义。
- 本地目录扫描必须只读，不能格式化、生成代码或修改被扫描工作区。
- 管理端治理页使用 Ant Design Modal、Table、Tabs 体系；用户端聊天页使用项目自定义主题令牌，必须保留亮暗主题可读性。
- `CONFIRM` 在当前 MVP 中等同阻断工具执行，因为还没有 Electron 二次确认回写链路。

## Extension Points

- 项目画像可以扩展 `governance_project_profile` 字段，补充模块地图、测试命令、关键入口、风险点和 Agent 输入上下文。
- 长期记忆可以作为新的治理子表和服务接入 `ChatApplicationService.buildAiHistory`，与技能/专家/搜索证据同级注入。
- 管理端治理中心可继续拆分子组件，避免单页持续膨胀。
- 用户端本地 workspace 切换处可以展示项目画像和记忆状态，但应复用 `useChatWorkspace` 的工作空间状态。

## Common Pitfalls

- 不要把项目画像写成 `docs/features/index.md` 的已完成功能，除非真实链路已经实现并验证。
- 不要直接读取任意路径后写入结果文件；画像扫描服务应通过数据库保存结果或显式返回给前端。
- 不要让长期记忆无条件注入所有聊天；必须按用户、项目、工作空间和启用状态过滤。
- 不要把会话摘要当作长期记忆。`chat_conversation_summary` 是短期上下文压缩，不是跨会话项目级或用户级记忆。
