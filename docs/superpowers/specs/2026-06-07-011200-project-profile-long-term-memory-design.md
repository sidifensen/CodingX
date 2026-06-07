# Project Profile And Long-Term Memory Design

**Status:** Approved
**Date:** 2026-06-07

## Background

`docs/project/mewcode-capability-gap-roadmap.md` lists two adjacent gaps: project code indexing/repository profiling, and context compression plus long-term memory. The reference page at `https://xiaolincoding.com/project/mewcode.html` describes these as Agent memory-layer capabilities: the Agent should understand the repository before acting, compress long conversations to control token use, and reuse project/user memory across sessions without losing key constraints.

CodingX already has a governance workbench, workspace binding, a basic `governance_project_profile` table, and `ConversationSummaryService` for single-conversation compression. The missing part is the complete loop: scan richer repository context, surface it in user/admin UI, create explicit-consent long-term memory, retrieve active memory, and inject it into model context.

## Goals

- Enrich project profiles from a workspace scan with module map, test commands, key entry points, risk points, and an Agent-ready context string.
- Add long-term memory records with user/project scope, automatic activation after explicit memory intent, active retrieval, and disable governance.
- Generate deterministic ACTIVE memory records from completed chat exchanges only when the user explicitly asks the Agent to remember something.
- Inject latest project profile and ACTIVE matching memories into chat model context.
- Expose project profile and memory status in user-side local workspace UI and management controls in the admin governance center.
- Keep existing single-conversation summary compression intact and semantically separate from long-term memory.

## Non-Goals

- No vector database or embedding search in this iteration.
- No LLM-based memory extraction; extraction is deterministic and testable.
- No silent memory extraction from ordinary chat text.
- No real SubAgent, Worktree, or Agent Teams implementation.

## Architecture

### Project Profile

`ProjectProfileService` remains the write entry for repository scans, but its output expands beyond the current tech stack list. The scanner will inspect only file and directory metadata plus selected small manifest files. It skips heavy/generated directories such as `.git`, `node_modules`, `target`, `dist`, `build`, `logs`, and `.idea`.

The profile stores:

- `module_map_json`: module code, path, type, language, package manager, and description.
- `test_commands_json`: canonical verification commands inferred from Maven and package scripts.
- `key_entrypoints_json`: controllers, app bootstraps, frontend main files, and config files.
- `risk_points_json`: deterministic repository risks, such as dirty generated folders, missing test script, large page files, or missing backend/frontend markers.
- `agent_context`: a compact Chinese context block for prompt injection.

Existing `tech_stack_json`, `entrypoints_json`, and `verification_commands_json` stay for compatibility. They will be populated from the richer scan results.

### Long-Term Memory

A new `governance_long_term_memory` table stores memory records. Each record has:

- `memory_scope`: `USER` or `PROJECT`.
- `user_id`: owner for user memory and candidate creator.
- `workspace_id`: required for project memory, optional for user memory.
- `memory_key`: deterministic dedupe key.
- `content`: concise memory text.
- `status`: `ACTIVE` or `REJECTED`; legacy `PENDING` rows are migrated to `ACTIVE`.
- `source_type`, `source_conversation_id`, `source_message_id`: extraction provenance.
- `keyword_json`: searchable keywords for deterministic retrieval.
- `confidence_score`, `last_used_at`, timestamps, and logical delete.

`LongTermMemoryService` handles extraction, status transitions, admin/user listing, and retrieval. Extraction looks for explicit Chinese/English memory signals in completed exchanges, including `记住`, `以后`, `偏好`, `项目约定`, `规范`, `always`, and `remember`. Because those phrases are the user's consent to persist the preference, the service creates `ACTIVE` records immediately and deduplicates by deterministic key. `ACTIVE` records are retrieved by workspace/user scope and keyword overlap with the current question. If there is no keyword overlap, user-level always-on preference records may still be returned within a small limit. `REJECTED` means disabled and excluded from prompt injection.

### Prompt Injection

`GovernanceAgentContextService` builds a single optional system prompt segment for `ChatApplicationService.buildAiHistory`.

The segment contains:

1. Latest project profile for the current workspace, if available.
2. Matching ACTIVE long-term memories for the current user and workspace.
3. Rules that these are contextual constraints, not user-visible content to quote verbatim.

This segment is appended with existing system intent, Plan mode, search evidence, expert context, and skill context. It does not replace `ConversationSummaryService`; short-term summary remains the conversation-level compression mechanism.

### User UI

The user-side local workspace header shows project profile and memory state when a local repository is bound:

- Scan status and profile summary.
- Compact chips for module count, test command count, risk count, and active memory count.
- A refresh/scan action that calls the workspace profile endpoint.
- A small active-memory list that shows what will be reinjected into future model context.

The UI uses existing theme tokens and supports light/dark mode. It must not use native `alert`, `confirm`, or `prompt`.

### Admin UI

The admin governance center keeps the existing tabs and adds:

- Richer project profile columns and detail summary.
- A `长期记忆` tab listing memory records with scope, workspace, status, source, keywords, timestamps, and enable/disable actions.
- Filters are intentionally minimal in this iteration; the backend limits result size.

Because `GovernanceCenterPage.tsx` is already large, new display helpers should be small and focused. A full page split can be a later refactor if needed.

## Error Handling

- Workspace scan rejects missing or non-directory paths with Chinese `BusinessException` messages.
- Memory status updates reject unsupported statuses and cross-user access.
- Prompt injection is fail-soft: if profile or memory retrieval returns no data, the chat flow continues without that segment.
- Admin/user API errors must surface the backend `ApiResponse.message`.

## Testing

Backend TDD coverage:

- Project profile scan emits modules, test commands, key entry points, risk points, and Agent context.
- Long-term memory extraction creates ACTIVE records after explicit memory intent and deduplicates by key.
- Status updates only allow ACTIVE/REJECTED so users or admins can enable or disable an existing memory.
- Retrieval returns only ACTIVE matching records within user/project scope.
- Governance context injects latest profile and active memory.
- Schema compatibility covers new table and added project profile fields.

Frontend verification:

- `npm run build` and `npm run test:run` for user and admin frontends.
- Browser/CDP verification for user local workspace profile/memory state and admin governance memory/profile tabs, including dark-mode readability or computed style evidence.
