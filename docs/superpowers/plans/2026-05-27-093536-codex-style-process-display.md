# Codex Style Process Display Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render thinking, web search, and shell command process events in a Codex-style collapsed process view.

**Architecture:** Keep the backend event contract unchanged and normalize process-card presentation inside `ChatView.tsx`. Add focused tests in `ChatView.test.tsx` to prove search grouping, command grouping, and default thinking visibility.

**Tech Stack:** React, TypeScript, Vitest, Testing Library, Tailwind-style utility classes.

---

### Task 1: Search And Command Process Grouping

**Files:**
- Modify: `frontend/user/src/views/ChatView.tsx`
- Test: `frontend/user/tests/views/ChatView.test.tsx`

- [ ] **Step 1: Write failing tests**

Add tests that render:

```tsx
processCards: [
  {
    id: 'search-result-1',
    type: 'tool_result',
    title: '观察',
    summary: '网页搜索返回 OpenAI：OpenAI API 文档',
    status: 'completed',
    toolId: 'search',
    displayName: '网页搜索',
    details: [{ label: '结果', content: 'OpenAI API 文档\nOpenAI\nhttps://platform.openai.com/docs' }],
  },
  {
    id: 'search-result-2',
    type: 'tool_result',
    title: '观察',
    summary: '网页搜索返回 Microsoft Learn：Bing 文档',
    status: 'completed',
    toolId: 'search',
    displayName: '网页搜索',
    details: [{ label: '结果', content: 'Bing 文档\nMicrosoft Learn\nhttps://learn.microsoft.com/bing/search-apis/' }],
  },
]
```

Expected before implementation: no `已搜索网页 2 次` row exists.

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
cd frontend/user
npm run test:run -- ChatView.test.tsx -t "Codex"
```

Expected: FAIL because the new summary row is absent.

- [ ] **Step 3: Implement grouping**

In `ChatView.tsx`, extend `ProcessTraceSegment` with `search_summary` and `command_summary`. Update `groupProcessTraceSegments` to collect adjacent search cards and adjacent shell command cards into those segment types.

- [ ] **Step 4: Render summary components**

Add `ProcessSearchSummary` and `ProcessCommandSummary` components. Both default collapsed, use existing theme tokens, and delegate expanded details to `ProcessToolRow` or a shell detail block.

- [ ] **Step 5: Verify GREEN**

Run:

```bash
cd frontend/user
npm run test:run -- ChatView.test.tsx -t "Codex"
```

Expected: PASS.

### Task 2: Shell Detail Block

**Files:**
- Modify: `frontend/user/src/views/ChatView.tsx`
- Test: `frontend/user/tests/views/ChatView.test.tsx`

- [ ] **Step 1: Write failing test**

Add a test with two shell command call/result pairs. Expected behavior:

```tsx
expect(screen.getByText('已运行 2 条命令')).toBeInTheDocument();
expect(screen.queryByText('$ Get-ChildItem')).not.toBeInTheDocument();
fireEvent.click(screen.getByTestId('process-command-summary-toggle-<messageId>-<firstId>'));
expect(screen.getByText('$ Get-ChildItem')).toBeInTheDocument();
expect(screen.getByText('package.json')).toBeInTheDocument();
```

- [ ] **Step 2: Run test to verify RED**

Run the focused test command above. Expected: FAIL.

- [ ] **Step 3: Implement shell parsing**

Add helpers that identify shell cards by `toolId`, `displayName`, title, or JSON parameter content. Extract command from `参数` detail JSON and output from `结果` detail.

- [ ] **Step 4: Implement shell UI**

Render a compact block with a `Shell` label, `$ command`, output preview, and status text. Keep background opaque and use existing `bg-surface-container`, `border-border`, `text-foreground`, and `text-muted` tokens.

- [ ] **Step 5: Verify GREEN**

Run the focused test command. Expected: PASS.

### Task 3: Full Verification And Long Task

**Files:**
- No production file changes expected unless verification exposes a bug.

- [ ] **Step 1: Run frontend verification**

Run:

```bash
cd frontend/user
npm run build
npm run test:run
```

Expected: both pass.

- [ ] **Step 2: Run backend sanity verification**

Run:

```bash
cd backend
mvn compile
mvn test
```

Expected: both pass or fail only for unrelated pre-existing dirty work, which must be reported with evidence.

- [ ] **Step 3: Start services for browser verification**

Check ports 5001, 5002, and 5003. Start the backend and user frontend if needed.

- [ ] **Step 4: Run one long chat task**

Use CDP on `http://localhost:5002` to trigger a prompt that causes thinking, web search, and local shell/tool output. Save screenshot under `logs/`.

- [ ] **Step 5: Iterate if not visually aligned**

If the screenshot does not show thinking, collapsed search summary, collapsed command summary, and readable expanded details, fix the mismatch and repeat the focused tests plus screenshot.
