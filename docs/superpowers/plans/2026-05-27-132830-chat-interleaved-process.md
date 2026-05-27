# Chat Interleaved Process Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render assistant text and process events in one chronological message flow instead of stacking all process cards above the answer.

**Architecture:** Add optional message-level timeline items for runtime ordering while preserving existing `content` and `processCards` compatibility fields. Stream handlers update both the legacy fields and the ordered timeline; `ChatView` renders the timeline when present and falls back to legacy rendering for historical messages.

**Tech Stack:** React, TypeScript, Vitest, Testing Library.

---

### Task 1: Add Failing Tests For Chronological Timeline

**Files:**
- Modify: `frontend/user/tests/views/chat/useChatWorkspace.test.ts`
- Modify: `frontend/user/tests/views/ChatView.test.tsx`

- [ ] **Step 1: Write hook RED test**

Add a test that emits `message -> tool-call start -> tool-call complete -> message` and asserts `timelineItems` order.

- [ ] **Step 2: Run hook RED test**

Run: `cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.test.ts -t "按流式事件顺序记录正文与工具过程时间线"`

Expected: FAIL because `timelineItems` is undefined.

- [ ] **Step 3: Write component RED test**

Add a test that renders one assistant message with `timelineItems` and asserts DOM order: first text before tool, tool before second text.

- [ ] **Step 4: Run component RED test**

Run: `cd frontend/user && npm run test:run -- tests/views/ChatView.test.tsx -t "按时间线穿插渲染助手正文与过程节点"`

Expected: FAIL because `ChatView` ignores `timelineItems`.

### Task 2: Implement Timeline State And Rendering

**Files:**
- Modify: `frontend/user/src/views/chat/types.ts`
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`
- Modify: `frontend/user/src/views/ChatView.tsx`

- [ ] **Step 1: Add timeline types**

Add `MessageTimelineItem` and optional `timelineItems?: MessageTimelineItem[]` to `ChatMessageItem`.

- [ ] **Step 2: Add stream merge helpers**

Add helpers that append content deltas to the latest content segment and upsert process cards into timeline items without moving existing items.

- [ ] **Step 3: Wire SSE events**

Update `message`, `thinking`, `tool-call` / `mcp-call`, `step(search)`, `reference`, `finish`, `cancel`, and `error` handlers to maintain `timelineItems` alongside `content` and `processCards`.

- [ ] **Step 4: Render mixed timeline**

Add an assistant message body renderer that iterates `timelineItems`, renders content with `MarkdownMessage`, and renders contiguous process items with `ProcessTracePanel`.

- [ ] **Step 5: Preserve legacy fallback**

If `timelineItems` is absent or empty, keep existing `processCards` then `content` rendering.

### Task 3: Verification And Commit

**Files:**
- Modify: `docs/features/index.md`
- Create or modify: `docs/features/chat/interleaved-process-timeline.md`

- [ ] **Step 1: Update feature docs**

Document the current frontend-only timeline behavior and compatibility boundary.

- [ ] **Step 2: Run focused and full frontend verification**

Run:

```bash
cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.test.ts -t "按流式事件顺序记录正文与工具过程时间线"
cd frontend/user && npm run test:run -- tests/views/ChatView.test.tsx -t "按时间线穿插渲染助手正文与过程节点"
cd frontend/user && npm run test:run
cd frontend/user && npm run build
```

- [ ] **Step 3: Run CDP visual verification**

Start or reuse the user frontend at `http://localhost:5002`, open it with CDP, verify a message containing interleaved process blocks, and save a screenshot under `logs/`.

- [ ] **Step 4: Review and commit**

Review `git diff`, stage only touched files, and commit with `feat(chat): 按顺序穿插展示消息过程时间线` if verification passes.
