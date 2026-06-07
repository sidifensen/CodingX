# Acceptance Criteria: Project Profile And Long-Term Memory

**Spec:** `docs/superpowers/specs/2026-06-07-011200-project-profile-long-term-memory-design.md`
**Date:** 2026-06-07
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | Project profile scan captures module map, test commands, key entrypoints, risk points, and Agent context. | Logic | Temporary workspace contains `backend/pom.xml`, `frontend/user/package.json`, `frontend/admin/package.json`, source entry files, and a large page file. | `ProjectProfileServiceTest` asserts JSON fields contain backend/user/admin modules, `mvn test`, frontend build/test commands, entrypoint paths, at least one risk, and `agentContext` text. |
| AC-002 | Project profile schema changes are present in migration and baseline schema with Chinese comments. | Logic | Migration and `schema.sql` are readable. | `GovernanceSchemaCompatibilityTest` finds new profile columns and `governance_long_term_memory` table/comment definitions in both files. |
| AC-003 | Memory extraction creates PENDING candidates only when an exchange contains explicit memory signals. | Logic | A completed conversation exchange contains text such as `记住我的代码风格偏好`. | `LongTermMemoryServiceTest` verifies one saved memory has status `PENDING`, scope `USER`, source ids, keywords, and is not ACTIVE. |
| AC-004 | Memory extraction deduplicates equivalent candidates. | Logic | Repository already returns an existing non-deleted memory for the same deterministic key. | `LongTermMemoryServiceTest` verifies no additional save occurs for the duplicate candidate. |
| AC-005 | User confirmation changes candidate status to ACTIVE and rejection changes it to REJECTED. | Logic | A PENDING memory owned by the current user exists. | `LongTermMemoryServiceTest` verifies `updateUserMemoryStatus` persists `ACTIVE` or `REJECTED` and updates timestamp. |
| AC-006 | Active memory retrieval only returns matching ACTIVE records scoped to current user or workspace. | Logic | Repository contains ACTIVE, PENDING, REJECTED, other-user, and other-workspace records. | `LongTermMemoryServiceTest` verifies retrieval includes only ACTIVE matching records for the supplied user/workspace/query. |
| AC-007 | Chat model context includes latest project profile and active long-term memory when available. | Logic | `GovernanceAgentContextService` is given a latest profile and active retrieved memories. | `GovernanceAgentContextServiceTest` verifies returned context contains project profile summary, module/test/risk snippets, memory content, and context usage rules. |
| AC-008 | Completed normal chat exchanges trigger deterministic memory candidate extraction after assistant completion. | Logic | `ChatApplicationService` completes a normal persisted assistant response. | `ChatApplicationServiceTest` verifies `GovernanceAgentContextService.extractMemoryCandidates` receives conversation, user message, and assistant message. |
| AC-009 | User workspace binding response includes profile and memory status after local repository binding. | API | `ChatWorkspaceBindingController` service returns binding result plus profile status view. | Controller test verifies JSON includes `projectProfile.summary` and `pendingMemoryCount`. |
| AC-010 | Admin governance API lists and updates long-term memories. | API | Admin controller has mocked memory service responses. | Controller test verifies `GET /api/admin/governance/long-term-memories` and `PATCH /api/admin/governance/long-term-memories/{id}/status` delegate to service and return `ApiResponse.success=true`. |
| AC-011 | User API lists pending/active memories and updates candidate status without native dialogs. | API | User controller has mocked memory service responses. | Controller test verifies `GET /api/chat/memories` and `PATCH /api/chat/memories/{id}/status` return normalized memory records and Chinese errors are handled by global exception handler. |
| AC-012 | User frontend displays bound workspace profile and pending memory count in both light and dark themes. | UI interaction | Backend/user frontend are running and a local workspace is selected or mocked by development state. | CDP evidence shows profile/memory container has non-transparent background and readable text in dark mode. |
| AC-013 | Admin governance center exposes long-term memory records and project profile details. | UI interaction | Backend/admin frontend are running with admin login. | CDP screenshot or computed style evidence shows `长期记忆` tab and project profile columns render without overlap and with readable dark-mode styling. |
| AC-014 | Feature documentation reflects the true implemented profile and long-term memory flow. | Logic | Implementation is complete. | `docs/features/governance/project-profile-long-term-memory.md` exists and `docs/features/index.md` links to it. |
