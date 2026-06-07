# Project Profile And Long-Term Memory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete deterministic project profile and long-term memory loop for CodingX, including scan, explicit-consent memory activation, retrieval, prompt injection, user UI, admin UI, docs, tests, and commit.

**Architecture:** Extend the existing governance subsystem rather than introducing a separate memory subsystem. Project profile scan writes richer JSON fields and Agent context into `governance_project_profile`; `LongTermMemoryService` saves explicit memory requests as ACTIVE records and manages ACTIVE/REJECTED status; `GovernanceAgentContextService` combines latest profile and active memory for chat prompt injection.

**Tech Stack:** Java 21, Spring Boot 3.4, MyBatis Plus, PostgreSQL migrations, Hutool, React 19, TypeScript, Tailwind tokens, Ant Design admin components.

---

## File Structure

- Modify `backend/src/main/resources/db/migration/V20260607_011500__project_profile_long_term_memory.sql`: add migration for profile columns and memory table.
- Modify `backend/src/main/resources/db/schema.sql`: mirror profile columns and memory table with Chinese comments.
- Modify governance domain/DO/repository/service/controller files under `backend/src/main/java/com/codingx/governance/`.
- Create `LongTermMemoryService`, `GovernanceAgentContextService`, memory domain model, repository interface, DO, mapper, repository implementation, and response/request DTOs.
- Modify `ChatApplicationService` to inject governance context and extract candidates after successful normal responses.
- Modify `ChatWorkspaceBindingService` and `ChatWorkspaceBindingController` to return profile/memory status to user side.
- Modify `frontend/user/src/views/chat/types.ts`, `chatApi.ts`, `useChatWorkspace.ts`, and `ChatView.tsx` for profile/memory state and actions.
- Modify `frontend/admin/src/api/adminChatApi.ts` and `frontend/admin/src/pages/GovernanceCenterPage.tsx` for memory management and richer profile display.
- Add or update backend tests under `backend/src/test/java/com/codingx/governance/` and affected chat/controller tests.
- Add feature docs under `docs/features/governance/project-profile-long-term-memory.md` and update `docs/features/index.md`.

## Tasks

### Task 1: Backend Schema And Domain

- [ ] Write failing schema compatibility assertions for new profile columns and `governance_long_term_memory`.
- [ ] Add migration and schema fields with Chinese comments.
- [ ] Extend `GovernanceProjectProfile` and `GovernanceProjectProfileDO`.
- [ ] Add `GovernanceLongTermMemory` domain, DO, mapper, repository interface, and repository implementation.
- [ ] Run schema/domain tests and make them pass.

### Task 2: Project Profile Scan

- [ ] Extend `ProjectProfileServiceTest` to assert modules, test commands, entrypoints, risks, and Agent context.
- [ ] Implement deterministic scanner helpers using Hutool/string utilities where useful.
- [ ] Preserve compatibility fields while filling new JSON fields.
- [ ] Run `mvn -Dtest=ProjectProfileServiceTest test`.

### Task 3: Long-Term Memory Service

- [ ] Add failing `LongTermMemoryServiceTest` for explicit memory extraction, dedupe, status updates, and retrieval.
- [ ] Implement extraction, keyword normalization, status transitions, list methods, and retrieval.
- [ ] Add user/admin request/response DTOs with field comments.
- [ ] Run `mvn -Dtest=LongTermMemoryServiceTest test`.

### Task 4: Governance Context And Chat Integration

- [ ] Add failing `GovernanceAgentContextServiceTest` for project profile plus active memory context.
- [ ] Add failing chat application assertion that completed normal exchanges trigger explicit memory extraction.
- [ ] Implement `GovernanceAgentContextService`.
- [ ] Inject governance context into `ChatApplicationService.buildAiHistory`.
- [ ] Trigger memory candidate extraction after successful assistant completion.
- [ ] Run targeted chat/governance tests.

### Task 5: User And Admin APIs

- [ ] Add controller tests for user memory APIs and admin memory APIs.
- [ ] Extend workspace binding controller test for profile/memory status in bind response.
- [ ] Implement user memory controller and admin governance memory endpoints.
- [ ] Extend workspace binding status service response.
- [ ] Run controller tests.

### Task 6: Frontend User Experience

- [ ] Extend user chat types and API methods for project profile and long-term memory.
- [ ] Store profile/memory status in `useChatWorkspace`.
- [ ] Add a compact workspace intelligence strip and active memory summaries to `ChatView`.
- [ ] Ensure light/dark theme tokens are used and no native browser dialogs are introduced.
- [ ] Run `cd frontend/user && npm run build && npm run test:run`.

### Task 7: Frontend Admin Experience

- [ ] Extend admin API types/methods for long-term memory and profile fields.
- [ ] Add `长期记忆` governance tab with status actions.
- [ ] Expand project profile columns/detail rendering for module/test/risk/Agent context summaries.
- [ ] Run `cd frontend/admin && npm run build && npm run test:run`.

### Task 8: Docs, Browser Verification, Review, Commit

- [ ] Add feature documentation and update feature index.
- [ ] Run backend `mvn compile` and `mvn test`.
- [ ] Start backend/user/admin services after checking ports.
- [ ] Use CDP/browser verification for user and admin pages, saving evidence under `logs/`.
- [ ] Perform code review, fix important findings, run final verification.
- [ ] Commit with Chinese message `feat(governance): 完善项目画像与长期记忆闭环`.
