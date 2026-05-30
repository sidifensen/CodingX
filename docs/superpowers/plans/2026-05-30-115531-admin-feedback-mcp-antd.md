# Admin Feedback and MCP AntD Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Componentize the admin feedback page and rebuild the admin MCP management page using Ant Design components.

**Architecture:** Keep page files focused on API state orchestration. Move feedback and MCP display/form pieces into focused page-local components, and keep pure transformation helpers isolated from rendering.

**Tech Stack:** React 19, TypeScript, Ant Design 5, Vitest/Testing Library, VitePlus.

---

### Task 1: Feedback Component Extraction

**Files:**
- Create: `frontend/admin/src/pages/feedback/FeedbackFilterBar.tsx`
- Create: `frontend/admin/src/pages/feedback/FeedbackTable.tsx`
- Create: `frontend/admin/src/pages/feedback/feedbackPagination.ts`
- Modify: `frontend/admin/src/pages/FeedbackPage.tsx`
- Test: `frontend/admin/tests/pages/feedback/FeedbackFilterBar.test.tsx`
- Test: `frontend/admin/tests/pages/FeedbackPage.test.tsx`

- [ ] **Step 1: Write failing component test**

Add a test that imports `FeedbackFilterBar`, changes keyword/vote, and verifies `onFilter` receives trimmed keyword and staged vote only when the filter action fires.

- [ ] **Step 2: Verify RED**

Run `npm run test:run -- tests/pages/feedback/FeedbackFilterBar.test.tsx` in `frontend/admin`. It should fail because the component does not exist.

- [ ] **Step 3: Extract feedback components**

Move filter controls, table columns, vote tag rendering, date formatting, and pagination normalization out of `FeedbackPage.tsx` into focused files. Keep `FeedbackPage.tsx` responsible for API loading, state, and callback wiring.

- [ ] **Step 4: Verify GREEN**

Run `npm run test:run -- tests/pages/feedback/FeedbackFilterBar.test.tsx tests/pages/FeedbackPage.test.tsx` and confirm all feedback tests pass.

### Task 2: MCP AntD Refactor

**Files:**
- Create: `frontend/admin/src/pages/mcp/mcpTypes.ts`
- Create: `frontend/admin/src/pages/mcp/mcpUtils.ts`
- Create: `frontend/admin/src/pages/mcp/McpToolbar.tsx`
- Create: `frontend/admin/src/pages/mcp/McpTable.tsx`
- Create: `frontend/admin/src/pages/mcp/McpConfigModal.tsx`
- Create: `frontend/admin/src/pages/mcp/McpDeleteModal.tsx`
- Create: `frontend/admin/src/pages/mcp/McpPingResultModal.tsx`
- Modify: `frontend/admin/src/pages/MCP.tsx`
- Test: `frontend/admin/tests/pages/MCP.test.tsx`

- [ ] **Step 1: Write failing AntD rendering tests**

Extend `MCP.test.tsx` to require `.ant-table`, `.ant-btn`, `.ant-modal`, and `.ant-form` in MCP list and dialog flows.

- [ ] **Step 2: Verify RED**

Run `npm run test:run -- tests/pages/MCP.test.tsx` in `frontend/admin`. It should fail on the AntD selectors because MCP currently uses native table/form/dialog markup.

- [ ] **Step 3: Rebuild MCP UI with AntD**

Replace native MCP controls with AntD `Button`, `Table`, `Alert`, `Tag`, `Badge`, `Modal`, `Form`, `Input`, `InputNumber`, `Switch`, `Descriptions`, `Space`, and `Typography`. Preserve current API calls, error text, pagination, and modal behaviors.

- [ ] **Step 4: Verify GREEN**

Run `npm run test:run -- tests/pages/MCP.test.tsx` and confirm all MCP tests pass.

### Task 3: Integrated Verification

**Files:**
- Modify as needed only in files touched by Tasks 1 and 2.

- [ ] **Step 1: Run targeted tests**

Run `npm run test:run -- tests/pages/FeedbackPage.test.tsx tests/pages/feedback/FeedbackFilterBar.test.tsx tests/pages/MCP.test.tsx`.

- [ ] **Step 2: Run build**

Run `npm run build` in `frontend/admin`.

- [ ] **Step 3: Browser verify**

Use CDP against `http://localhost:5003/feedbacks` and `http://localhost:5003/mcp`, save screenshots under `logs/`, and verify AntD table roots plus readable container backgrounds.
