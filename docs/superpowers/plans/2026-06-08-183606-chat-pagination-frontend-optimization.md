# Chat Pagination and Frontend Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace full chat conversation/message loading with paginated loading and reduce frontend structural hotspots without changing the visible chat workflow.

**Architecture:** Backend keeps old list endpoints compatible while adding cursor pagination response paths. Frontend moves default chat workspace loading to paginated API calls, exposes explicit load-more actions, and splits rendering/request construction into smaller files.

**Tech Stack:** Spring Boot 3.4, MyBatis-Plus, React 19, TypeScript 5.8, Vitest, Testing Library.

---

## Files

- Modify: `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatController.java`
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatConversationApplicationService.java`
- Modify: `backend/src/main/java/com/codingx/chat/domain/repository/conversation/ChatConversationRepository.java`
- Modify: `backend/src/main/java/com/codingx/chat/domain/repository/conversation/ChatMessageRepository.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/repository/conversation/ChatConversationRepositoryImpl.java`
- Modify: `backend/src/main/java/com/codingx/chat/infrastructure/persistence/repository/conversation/ChatMessageRepositoryImpl.java`
- Create: `backend/src/main/java/com/codingx/chat/interfaces/response/CursorPageResponse.java`
- Create: `backend/src/test/java/com/codingx/chat/application/service/ChatConversationPaginationTest.java`
- Modify: `frontend/user/src/views/chat/types.ts`
- Modify: `frontend/user/src/views/chat/chatApi.ts`
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`
- Modify: `frontend/user/src/views/ChatView.tsx`
- Modify: `frontend/user/src/components/Sidebar.tsx`
- Create: `frontend/user/src/components/sidebar/ConversationHistory.tsx`
- Create: `frontend/user/src/components/sidebar/ConversationRow.tsx`
- Create: `frontend/user/src/views/chat/ChatMessageList.tsx`
- Create: `frontend/user/tests/views/chat/chatPaginationApi.test.ts`
- Create: `frontend/user/tests/views/chat/useChatWorkspace.pagination.test.ts`
- Modify: `.gitignore`
- Modify: `frontend/user/package.json`
- Modify: `frontend/admin/package.json`
- Modify: `docs/features/index.md`
- Create: `docs/features/chat/paginated-chat-history.md`

## Task 1: Backend Cursor Pagination

- [ ] Add failing backend tests for conversation and message pagination.
- [ ] Add `CursorPageResponse<T>` with `items`, `hasMore`, `nextCursor`.
- [ ] Add repository methods for workspace conversation cursor pages and message pages.
- [ ] Update application service to normalize page size, validate ownership, and return cursor pages.
- [ ] Update controller to return old array response when no pagination params are present and cursor response when pagination params are present.
- [ ] Run targeted backend pagination tests and then `mvn test` if targeted tests pass.

## Task 2: Frontend Paginated API

- [ ] Add failing `chatPaginationApi.test.ts` for `listConversationPage`, `listMessagePage`, and stream request helper.
- [ ] Add pagination types to `types.ts`.
- [ ] Implement `ChatApi.listConversationPage`, `ChatApi.listMessagePage`, and centralized stream request helpers.
- [ ] Keep old `listConversations` and `listMessages` as compatibility wrappers.
- [ ] Run targeted frontend API tests.

## Task 3: Workspace Pagination State

- [ ] Add failing `useChatWorkspace.pagination.test.ts` for bootstrap first page, load more conversations, select conversation recent message page, and load older messages.
- [ ] Add pagination state to `useChatWorkspace` and expose `loadMoreConversations` / `loadOlderMessages`.
- [ ] Replace bootstrap and selection calls with paginated API calls.
- [ ] Preserve existing local snapshot, task reminder, active stream, and workspace partition behavior for loaded records.
- [ ] Run targeted hook tests.

## Task 4: Frontend Component Split

- [ ] Move Sidebar history list and row rendering into `components/sidebar/ConversationHistory.tsx` and `ConversationRow.tsx`.
- [ ] Wire Sidebar “查看更多” to `loadMoreConversations` instead of local slicing.
- [ ] Move ChatView message list rendering into `views/chat/ChatMessageList.tsx`.
- [ ] Trigger `loadOlderMessages` from the top of the chat scroll region while preserving scroll position.
- [ ] Keep dark-mode classes and existing test IDs where current tests depend on them.

## Task 5: Repository Hygiene and Metadata

- [ ] Remove `/docs/` from `.gitignore` and verify `git check-ignore docs/features/example.md` does not ignore docs.
- [ ] Rename frontend package names to `codingx-user-frontend` and `codingx-admin-frontend`.
- [ ] Replace `rm -rf` clean scripts with Node-based `fs.rmSync` commands.
- [ ] Add or update feature documentation for paginated chat history.

## Task 6: Verification and Commit

- [ ] Run backend validation for changed backend surface: `cd backend && mvn compile && mvn test`.
- [ ] Run user frontend validation: `cd frontend/user && npm run build && npm run test:run`.
- [ ] Run admin frontend validation after package script edits: `cd frontend/admin && npm run build && npm run test:run`.
- [ ] If frontend UI changed, run browser verification on `http://localhost:5002` with screenshot or computed-style evidence.
- [ ] Review `git diff` to ensure unrelated dirty files were not modified or staged.
- [ ] Stage only this task's files and commit with Chinese message `feat(chat): 优化聊天历史分页加载与前端结构`.
