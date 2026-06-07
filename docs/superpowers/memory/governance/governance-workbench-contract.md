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
  - backend/src/main/java/com/codingx/chat/interfaces/controller/ChatMemoryController.java
  - frontend/user/src/views/MemoryView.tsx
  - backend/src/main/resources/db/schema.sql
  - backend/src/main/resources/db/migration/V20260606_211000__create_governance_workbench_tables.sql
related_docs:
  - docs/features/governance/governed-workbench.md
  - docs/superpowers/memory/governance/governance-workbench-module-card.md
entrypoints:
  - backend/src/main/java/com/codingx/governance/interfaces/controller/AdminGovernanceController.java
  - backend/src/main/java/com/codingx/chat/interfaces/controller/ChatMemoryController.java
  - backend/src/main/java/com/codingx/governance/application/service/ProjectProfileService.java
  - backend/src/main/java/com/codingx/governance/application/service/LongTermMemoryService.java
last_verified_commit: 8f68ede52a23d0fdb7156ec519e7df23f981d16c
status: active
---

# Governance Workbench Contract

## Scope

本契约覆盖当前治理工作台的管理端接口、用户端命令目录、项目画像表、长期记忆和聊天上下文注入点。它不覆盖未实现的真实 SubAgent、Worktree 或 Agent Teams。

## Producers And Consumers

- `AdminGovernanceController` 生产管理端治理数据，`frontend/admin/src/pages/GovernanceCenterPage.tsx` 消费。
- `SlashCommandController` 生产用户端启用命令目录，`frontend/user/src/views/chat/useChatWorkspace.ts` 与 `ChatView.tsx` 消费。
- `ProjectProfileService` 生产 `GovernanceProjectProfile`，`GovernanceProjectProfileRepositoryImpl` 写入 `governance_project_profile`。
- `LongTermMemoryService` 生产和治理 `GovernanceLongTermMemory`，`ChatMemoryController` 向用户端暴露查询、编辑、启停和删除接口。
- `GovernanceAgentContextService` 消费项目画像和 ACTIVE 长期记忆，`ChatApplicationService.buildAiHistory` 将其与系统提示、Plan mode、搜索证据、专家和技能上下文一起注入模型历史。
- `frontend/user/src/views/MemoryView.tsx` 消费用户侧长期记忆接口，为当前账号提供用户记忆和项目记忆的管理面。

## Interface Rules

- `GET /api/admin/governance/project-profiles?limit=N` 返回最近项目画像，`limit` 应在仓储层限制到安全范围。
- `POST /api/admin/governance/project-profiles/scan` 接收 `workspaceId` 与 `workspacePath`，路径存在性、目录合法性和扫描细节由 service 处理。
- `GET /api/chat/slash-commands` 只返回启用命令；内置命令模板的拼接由后端 `SlashCommandService` 完成，前端只提交结构化命令选择和用户正文。
- `GET /api/chat/memories` 返回当前登录用户可见的用户级和项目级长期记忆；用户端管理页需要传 `status=ALL` 后在本地按范围和状态筛选。
- `PATCH /api/chat/memories/{memoryId}` 只允许当前用户编辑自己的记忆正文，服务层必须同步刷新 `content`、`keywordJson`、`memoryKey` 和 `updatedAt`。
- `PATCH /api/chat/memories/{memoryId}/status` 只允许当前用户启用或停用自己的长期记忆，`ACTIVE` 参与模型上下文回注，`REJECTED` 不参与回注。
- `DELETE /api/chat/memories/{memoryId}` 执行逻辑删除，设置 `deleted=1` 后必须从用户列表和上下文检索中排除，但保留来源审计链路。
- `governance_project_profile` 当前字段包含 `workspace_id`、`workspace_path`、`summary`、`tech_stack_json`、`entrypoints_json`、`verification_commands_json`、`status`、`scanned_at` 和审计时间字段。

## Invariants

- 数据库结构变更必须同时更新迁移脚本和 `backend/src/main/resources/db/schema.sql`，新增表字段必须保留中文注释。
- 治理表的 Java DO、领域模型、仓储映射和前端 TypeScript 类型必须保持字段同步。
- 项目画像扫描失败应通过中文 `BusinessException` 或统一异常处理返回 `ApiResponse`，不要把 Java 异常栈暴露给前端。
- 长期记忆与短期摘要语义必须分离：长期记忆按用户/项目持久化，短期摘要只压缩单会话历史。
- 长期记忆不再使用确认流：只有用户消息出现“记住”“请记忆”“长期保存”“以后都按”“我的偏好”等显式授权信号才自动写入 `ACTIVE`，普通聊天不能沉淀。
- 用户端长期记忆管理必须使用项目内自定义弹窗、主题令牌和后端 `ApiResponse.message`，禁止用浏览器原生 `alert`、`confirm` 或 `prompt`。

## Compatibility Notes

- 管理端现有 `GovernanceCenterPage` 已是大文件；扩展项目画像和记忆页签时应优先抽组件和工具函数，降低页面继续膨胀的维护风险。
- `ProjectProfileServiceTest` 当前使用内存仓储验证 Maven/NPM 检测；扩展扫描字段时应先改测试，确保 TDD 红绿闭环。
- `ConversationSummaryService` 只做启发式文本拼接压缩；如果接入 LLM 摘要，应增加失败兜底，不能阻断正常聊天回复落库。
- `MemoryView` 是独立功能页，直接访问 `/memories` 时不能预加载聊天会话列表、样例问题等聊天首屏重型接口。
