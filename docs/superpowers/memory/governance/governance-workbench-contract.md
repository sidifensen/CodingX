---
type: contract
title: governance-workbench-contract
summary: 记录治理工作台现有接口、表结构和上下文注入契约，作为后续项目画像与长期记忆扩展基线。
tags:
  - governance
  - contract
  - database
owned_paths:
  - backend/src/main/java/com/codingx/governance
  - backend/src/main/resources/db/schema.sql
  - backend/src/main/resources/db/migration/V20260606_211000__create_governance_workbench_tables.sql
related_docs:
  - docs/features/governance/governed-workbench.md
  - docs/superpowers/memory/governance/governance-workbench-module-card.md
entrypoints:
  - backend/src/main/java/com/codingx/governance/interfaces/controller/AdminGovernanceController.java
  - backend/src/main/java/com/codingx/governance/application/service/ProjectProfileService.java
last_verified_commit: d9b47d7f535e549625b336ac0dbfd2eeac94f574
status: active
---

# Governance Workbench Contract

## Scope

本契约覆盖当前治理工作台的管理端接口、用户端命令目录、项目画像表和聊天上下文注入点。它不覆盖未实现的长期记忆、真实 SubAgent、Worktree 或 Agent Teams。

## Producers And Consumers

- `AdminGovernanceController` 生产管理端治理数据，`frontend/admin/src/pages/GovernanceCenterPage.tsx` 消费。
- `SlashCommandController` 生产用户端启用命令目录，`frontend/user/src/views/chat/useChatWorkspace.ts` 与 `ChatView.tsx` 消费。
- `ProjectProfileService` 生产 `GovernanceProjectProfile`，`GovernanceProjectProfileRepositoryImpl` 写入 `governance_project_profile`。
- `ChatApplicationService.buildAiHistory` 消费系统提示、Plan mode、搜索证据、专家和技能上下文；后续项目画像和长期记忆应在这里或其下沉服务中集中注入。

## Interface Rules

- `GET /api/admin/governance/project-profiles?limit=N` 返回最近项目画像，`limit` 应在仓储层限制到安全范围。
- `POST /api/admin/governance/project-profiles/scan` 接收 `workspaceId` 与 `workspacePath`，路径存在性、目录合法性和扫描细节由 service 处理。
- `GET /api/chat/slash-commands` 只返回启用命令；内置命令模板的拼接由后端 `SlashCommandService` 完成，前端只提交结构化命令选择和用户正文。
- `governance_project_profile` 当前字段包含 `workspace_id`、`workspace_path`、`summary`、`tech_stack_json`、`entrypoints_json`、`verification_commands_json`、`status`、`scanned_at` 和审计时间字段。

## Invariants

- 数据库结构变更必须同时更新迁移脚本和 `backend/src/main/resources/db/schema.sql`，新增表字段必须保留中文注释。
- 治理表的 Java DO、领域模型、仓储映射和前端 TypeScript 类型必须保持字段同步。
- 项目画像扫描失败应通过中文 `BusinessException` 或统一异常处理返回 `ApiResponse`，不要把 Java 异常栈暴露给前端。
- 长期记忆与短期摘要语义必须分离：长期记忆按用户/项目持久化，短期摘要只压缩单会话历史。

## Compatibility Notes

- 管理端现有 `GovernanceCenterPage` 已是大文件；扩展项目画像和记忆页签时应优先抽组件和工具函数，降低页面继续膨胀的维护风险。
- `ProjectProfileServiceTest` 当前使用内存仓储验证 Maven/NPM 检测；扩展扫描字段时应先改测试，确保 TDD 红绿闭环。
- `ConversationSummaryService` 只做启发式文本拼接压缩；如果接入 LLM 摘要，应增加失败兜底，不能阻断正常聊天回复落库。
