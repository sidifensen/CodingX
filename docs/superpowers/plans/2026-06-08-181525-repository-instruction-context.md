# Repository Instruction Context Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除项目画像功能，改为把仓库规范文件注入 Agent 上下文。

**Architecture:** `RepositoryInstructionContextService` 负责 workspace 路径解析、规则文件发现、读取、裁剪和日志；`GovernanceAgentContextService` 负责组合规范上下文与长期记忆。前后端删除项目画像字段、接口和 UI，数据库通过新迁移删除画像表并更新 schema 基线。

**Tech Stack:** Java 21、Spring Boot、MyBatis Plus、JUnit 5、Mockito、React、TypeScript、Vitest、Ant Design。

---

### Task 1: 后端规范文件上下文 TDD

**Files:**
- Create: `backend/src/test/java/com/codingx/governance/application/RepositoryInstructionContextServiceTest.java`
- Create: `backend/src/main/java/com/codingx/governance/application/service/RepositoryInstructionContextService.java`
- Modify: `backend/src/main/java/com/codingx/governance/application/service/GovernanceAgentContextService.java`
- Modify: `backend/src/test/java/com/codingx/governance/application/GovernanceAgentContextServiceTest.java`

- [ ] **Step 1: Write failing tests**

Add tests that create temp rule files, mock `WorkspaceMapper`, and assert included/excluded files, log preview, truncation, and combined governance context without project profile.

- [ ] **Step 2: Run RED**

Run:

```bash
cd backend && mvn -Dtest=RepositoryInstructionContextServiceTest,GovernanceAgentContextServiceTest test
```

Expected: FAIL because `RepositoryInstructionContextService` does not exist and `GovernanceAgentContextService` still depends on `ProjectProfileService`.

- [ ] **Step 3: Implement service**

Create `RepositoryInstructionContextService` with constants for supported candidates and exclusions, `buildInstructionContext(Long userId, Long workspaceId)`, workspace lookup through `WorkspaceMapper`, file discovery, UTF-8 reads, preview logging, and safe truncation.

- [ ] **Step 4: Update context service**

Replace project profile injection with repository instruction context injection, keep long-term memory retrieval unchanged, and update comments to describe the new source.

- [ ] **Step 5: Run GREEN**

Run the same Maven test command. Expected: PASS for the two changed tests.

### Task 2: 删除后端项目画像 API 和绑定响应

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatWorkspaceBindingService.java`
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatWorkspaceBindingController.java`
- Modify: `backend/src/main/java/com/codingx/governance/interfaces/controller/AdminGovernanceController.java`
- Modify: `backend/src/test/java/com/codingx/chat/application/service/chat/ChatWorkspaceBindingServiceTest.java`
- Modify: `backend/src/test/java/com/codingx/chat/interfaces/controller/ChatWorkspaceBindingControllerTest.java`
- Modify: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationServiceTest.java`
- Modify: `backend/src/test/java/com/codingx/chat/interfaces/controller/AdminGovernanceMemoryControllerTest.java`

- [ ] **Step 1: Write failing contract updates**

Update tests so binding results no longer include `projectProfile`, admin controller tests no longer mock project profile service, and chat application tests expect “仓库规范文件” instead of “项目画像”.

- [ ] **Step 2: Run RED**

Run:

```bash
cd backend && mvn -Dtest=ChatWorkspaceBindingServiceTest,ChatWorkspaceBindingControllerTest,ChatApplicationServiceTest,AdminGovernanceMemoryControllerTest test
```

Expected: FAIL while production code still exposes project profile fields and constructor dependencies.

- [ ] **Step 3: Remove production coupling**

Remove `ProjectProfileService` from binding service and admin controller, remove project profile scan/list endpoints and request record, remove `ProjectProfileView`, and shrink `WorkspaceBindingResult` / controller response to workspace fields plus `activeMemoryCount`.

- [ ] **Step 4: Run GREEN**

Run the same Maven test command. Expected: PASS for updated backend contract tests.

### Task 3: 删除项目画像持久化和数据库表

**Files:**
- Delete: `backend/src/main/java/com/codingx/governance/application/service/ProjectProfileService.java`
- Delete: `backend/src/main/java/com/codingx/governance/domain/model/GovernanceProjectProfile.java`
- Delete: `backend/src/main/java/com/codingx/governance/domain/repository/GovernanceProjectProfileRepository.java`
- Delete: `backend/src/main/java/com/codingx/governance/infrastructure/persistence/dataobject/GovernanceProjectProfileDO.java`
- Delete: `backend/src/main/java/com/codingx/governance/infrastructure/persistence/mapper/GovernanceProjectProfileMapper.java`
- Delete: `backend/src/main/java/com/codingx/governance/infrastructure/persistence/repository/GovernanceProjectProfileRepositoryImpl.java`
- Delete: `backend/src/test/java/com/codingx/governance/application/ProjectProfileServiceTest.java`
- Create: `backend/src/main/resources/db/migration/V20260608_181525__drop_governance_project_profile.sql`
- Modify: `backend/src/main/resources/db/schema.sql`
- Modify: `backend/src/test/java/com/codingx/governance/infrastructure/GovernanceSchemaCompatibilityTest.java`

- [ ] **Step 1: Write failing schema test update**

Update schema compatibility test to assert the new drop migration exists, `DROP TABLE IF EXISTS governance_project_profile` is present, and schema no longer creates the profile table or indexes.

- [ ] **Step 2: Run RED**

Run:

```bash
cd backend && mvn -Dtest=GovernanceSchemaCompatibilityTest test
```

Expected: FAIL because the drop migration is missing and schema still contains the table.

- [ ] **Step 3: Apply database deletion**

Add the drop migration, remove the profile table block and profile indexes from schema, and delete unused Java persistence/model files and old profile test.

- [ ] **Step 4: Run GREEN**

Run the same schema test. Expected: PASS.

### Task 4: 前端删除画像状态、API 和 UI

**Files:**
- Modify: `frontend/user/src/views/chat/types.ts`
- Modify: `frontend/user/src/views/chat/chatApi.ts`
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`
- Modify: `frontend/user/src/views/ChatView.tsx`
- Modify: `frontend/user/tests/views/chat/chatApi.test.ts`
- Modify: `frontend/user/tests/views/chat/useChatWorkspace.test.ts`
- Modify: `frontend/user/tests/views/ChatView.test.tsx`
- Modify: `frontend/admin/src/api/adminChatApi.ts`
- Modify: `frontend/admin/src/pages/GovernanceCenterPage.tsx`
- Modify: `frontend/admin/tests/api/adminChatApi.test.ts`
- Modify: `frontend/admin/tests/pages/GovernanceCenterPage.test.tsx`

- [ ] **Step 1: Write failing frontend expectations**

Update tests so user binding ignores `projectProfile`, the strip only depends on memories, and admin governance tests assert no project profile tab/API call.

- [ ] **Step 2: Run RED**

Run targeted frontend tests:

```bash
cd frontend/user && npm run test:run -- ChatView.test.tsx chatApi.test.ts useChatWorkspace.test.ts
cd frontend/admin && npm run test:run -- GovernanceCenterPage.test.tsx adminChatApi.test.ts
```

Expected: FAIL while UI/API still reference project profile.

- [ ] **Step 3: Remove frontend implementation**

Delete `ProjectProfileView` type, remove `projectProfile` state and response mapping, rename the strip copy to “工作区记忆”, remove JSON summary helpers if unused, delete admin profile API types/methods, tab, scan button and dialog.

- [ ] **Step 4: Run GREEN**

Run the same targeted tests. Expected: PASS.

### Task 5: 功能文档、全量验证和提交

**Files:**
- Modify: `docs/features/index.md`
- Modify: `docs/features/governance/governed-workbench.md`
- Replace or create: `docs/features/governance/repository-instruction-context-long-term-memory.md`
- Modify: `docs/superpowers/memory/governance/governance-workbench-contract.md`
- Modify: `docs/superpowers/memory/governance/governance-workbench-module-card.md`

- [ ] **Step 1: Update feature docs**

Document the current implementation: repository instruction context plus long-term memory, no project profile table or management tab.

- [ ] **Step 2: Run full backend verification**

Run:

```bash
cd backend && mvn compile && mvn test
```

Expected: exit code 0.

- [ ] **Step 3: Run full frontend verification**

Run:

```bash
cd frontend/user && npm run build && npm run test:run
cd frontend/admin && npm run build && npm run test:run
```

Expected: exit code 0 for all commands.

- [ ] **Step 4: Browser verification**

Start required dev servers if available, open the affected chat and admin pages through CDP, save at least one screenshot under `logs/`, and verify the UI no longer shows project profile controls.

- [ ] **Step 5: Review and commit**

Run code review, resolve Critical/Important issues, stage only files changed for this task, and commit with:

```bash
git commit -m "refactor(governance): 删除项目画像并加载仓库规范"
```
