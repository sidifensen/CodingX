# MewCode 能力缺口治理工作台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 roadmap 推荐顺序前 5 项能力的治理型 MVP，覆盖软件端权限、slash command、Hook、项目画像和计划步骤可视化。

**Architecture:** 后端新增 `governance` 模块负责策略、命令、Hook、画像和审计持久化；聊天流程和本地工具运行时只调用该模块做判定与记录。管理端新增治理中心页面，用户端聊天输入区新增命令目录和选择交互，Electron 仍只提供本地目录确认与 HostContext。

**Tech Stack:** Spring Boot、MyBatis Plus、PostgreSQL、Hutool、React、TypeScript、Ant Design、Vitest、Testing Library、Electron。

---

### Task 1: 后端治理数据模型与迁移

**Files:**
- Create: `backend/src/main/resources/db/migration/V20260606_211000__create_governance_workbench_tables.sql`
- Modify: `backend/src/main/resources/db/schema.sql`
- Create: `backend/src/main/java/com/codingx/governance/domain/model/*.java`
- Create: `backend/src/main/java/com/codingx/governance/infrastructure/persistence/dataobject/*.java`
- Create: `backend/src/main/java/com/codingx/governance/infrastructure/persistence/mapper/*.java`
- Create: `backend/src/main/java/com/codingx/governance/infrastructure/persistence/repository/*.java`
- Test: `backend/src/test/java/com/codingx/governance/infrastructure/GovernanceSchemaCompatibilityTest.java`

- [ ] Write failing schema compatibility test that asserts the six governance tables exist in migration and schema with comments.
- [ ] Add migration and schema definitions for permission policy/audit, hook rule/audit, project profile, slash command.
- [ ] Add domain models, DOs, mappers and repositories with complete Java comments.
- [ ] Run `mvn -Dtest=GovernanceSchemaCompatibilityTest test` and verify pass.

### Task 2: Slash Command 后端目录与解析

**Files:**
- Create: `backend/src/main/java/com/codingx/governance/application/service/SlashCommandService.java`
- Create: `backend/src/main/java/com/codingx/governance/interfaces/controller/SlashCommandController.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatStreamRequestApplicationService.java`
- Test: `backend/src/test/java/com/codingx/governance/application/SlashCommandServiceTest.java`
- Test: `backend/src/test/java/com/codingx/chat/interfaces/controller/ChatStreamControllerTest.java`

- [ ] Write failing tests for listing enabled commands and rejecting disabled/unknown commands.
- [ ] Implement command query and context building from `governance_slash_command`.
- [ ] Wire structured `slash_command` parsing for command types beyond `skill`.
- [ ] Run targeted backend tests and verify pass.

### Task 3: 权限策略与工具执行拦截

**Files:**
- Create: `backend/src/main/java/com/codingx/governance/application/service/PermissionPolicyService.java`
- Modify: `backend/src/main/java/com/codingx/tool/application/service/ChatToolExecutionService.java`
- Modify: `backend/src/main/java/com/codingx/tool/application/service/CodexBuiltinChatToolExecutor.java`
- Create: `backend/src/main/java/com/codingx/governance/interfaces/controller/AdminGovernanceController.java`
- Test: `backend/src/test/java/com/codingx/governance/application/PermissionPolicyServiceTest.java`
- Test: `backend/src/test/java/com/codingx/tool/application/service/ChatToolExecutionServiceTest.java`

- [ ] Write failing tests for DENY and CONFIRM policies before command execution.
- [ ] Implement policy matching by tool code, command pattern and path pattern.
- [ ] Add audit writes for allow/deny/confirm decisions.
- [ ] Add admin CRUD/query endpoints for policies and audits.
- [ ] Run targeted tests and verify pass.

### Task 4: Hook 规则与项目画像

**Files:**
- Create: `backend/src/main/java/com/codingx/governance/application/service/HookRuleService.java`
- Create: `backend/src/main/java/com/codingx/governance/application/service/ProjectProfileService.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`
- Test: `backend/src/test/java/com/codingx/governance/application/HookRuleServiceTest.java`
- Test: `backend/src/test/java/com/codingx/governance/application/ProjectProfileServiceTest.java`

- [ ] Write failing tests for Hook audit writes and project profile scanning.
- [ ] Implement no-side-effect Hook audit action.
- [ ] Trigger Hook service before/after model tool calls and at task completion.
- [ ] Implement project profile scanner for Maven/NPM/Vite/workspace markers.
- [ ] Run targeted tests and verify pass.

### Task 5: 前端管理端治理中心

**Files:**
- Modify: `frontend/admin/src/api/adminChatApi.ts`
- Modify: `frontend/admin/src/components/Layout.tsx`
- Modify: `frontend/admin/src/App.tsx`
- Create: `frontend/admin/src/pages/GovernanceCenterPage.tsx`
- Test: `frontend/admin/tests/pages/GovernanceCenterPage.test.tsx`
- Test: `frontend/admin/tests/api/adminChatApi.test.ts`

- [ ] Write failing API and page tests for governance dashboard loading.
- [ ] Add governance types and API methods.
- [ ] Add sidebar route and page tabs for policies, hooks, profiles, slash commands and audits.
- [ ] Use AntD Modal/Drawer for edits, no native dialogs.
- [ ] Run `npm run test:run -- GovernanceCenterPage adminChatApi` and verify pass.

### Task 6: 用户端 Slash Command 入口

**Files:**
- Modify: `frontend/user/src/views/chat/chatApi.ts`
- Modify: `frontend/user/src/views/chat/types.ts`
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`
- Modify: `frontend/user/src/views/ChatView.tsx`
- Test: `frontend/user/tests/views/ChatView.test.tsx`
- Test: `frontend/user/tests/views/chat/chatApi.test.ts`

- [ ] Write failing tests for `/` command panel and structured submit payload.
- [ ] Add command list API and chat workspace state.
- [ ] Render compact command panel using existing theme tokens and keyboard selection.
- [ ] Preserve existing MCP/skill button flows and avoid restoring old MCP slash panel.
- [ ] Run targeted user frontend tests and verify pass.

### Task 7: 功能文档、验证和提交

**Files:**
- Modify: `docs/features/index.md`
- Create: `docs/features/governance/governed-workbench.md`
- Modify: `docs/superpowers/memory/index.md` if durable contract changes need recording.

- [ ] Update feature docs with current real implementation only.
- [ ] Run backend compile/test and frontend build/test according to changed surface.
- [ ] Start affected services and run browser/CDP verification for management page and user command panel, saving screenshots under `logs/`.
- [ ] Stage only this session's files and commit with Chinese message `feat(governance): 补齐软件端治理工作台能力`。
