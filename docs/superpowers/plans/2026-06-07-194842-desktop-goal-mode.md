# Desktop Goal Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在桌面端聊天工作台增加目标模式切换、目标进度悬浮窗，并让目标模式请求驱动后端目标工具提示。

**Architecture:** 前端在 `useChatWorkspace` 中保存目标模式状态，通过现有 `planMode` query 参数传给后端；`ChatView` 只负责按钮和进度窗展示。后端只调整 `buildPlanModeContext` 的系统提示语义，不新增接口、数据库或 Electron 主进程能力。

**Tech Stack:** React、TypeScript、Vitest、Testing Library、Tailwind 主题令牌、Java 21、Spring Boot、JUnit 5。

---

### Task 1: Frontend Request State

**Files:**
- Modify: `frontend/user/src/views/chat/types.ts`
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`
- Test: `frontend/user/tests/views/chat/useChatWorkspace.test.ts`

- [ ] **Step 1: Write failing URL tests**

Add tests that call `buildStreamRequestUrl` with `goalModeEnabled=true` and `goalModeEnabled=false`.

Run: `npm run test:run -- tests/views/chat/useChatWorkspace.test.ts -t "目标模式"`
Expected: FAIL because `buildStreamRequestUrl` does not accept or emit the goal mode parameter yet.

- [ ] **Step 2: Add workspace state and URL parameter**

Add `goalModeEnabled: boolean` and `setGoalModeEnabled: (enabled: boolean) => void` to `ChatWorkspaceController`. Add `useState(false)` in `useChatWorkspace`, pass the state into `buildStreamRequestUrl` from `submitMessage`, and let `buildStreamRequestUrl` append `planMode=true` only when the new boolean is true.

- [ ] **Step 3: Verify focused frontend logic**

Run: `npm run test:run -- tests/views/chat/useChatWorkspace.test.ts -t "目标模式"`
Expected: PASS with both URL tests green.

### Task 2: Chat View Goal Controls

**Files:**
- Modify: `frontend/user/src/views/ChatView.tsx`
- Test: `frontend/user/tests/views/ChatView.test.tsx`

- [ ] **Step 1: Write failing UI tests**

Add tests for the goal mode button, click callback, progress panel visibility when enabled/steps exist, and hidden state when idle.

Run: `npm run test:run -- tests/views/ChatView.test.tsx -t "目标模式"`
Expected: FAIL because the button and `goal-progress-panel` do not exist.

- [ ] **Step 2: Implement button and progress panel**

Import lucide `Target`, destructure `goalModeEnabled` and `setGoalModeEnabled`, place the button next to deep thinking, and add a fixed right-side `GoalProgressPanel` section using theme tokens. Compute progress from `executionSteps`, `isStreaming`, and terminal step statuses.

- [ ] **Step 3: Verify focused UI tests**

Run: `npm run test:run -- tests/views/ChatView.test.tsx -t "目标模式"`
Expected: PASS with all goal mode UI tests green.

### Task 3: Backend Prompt Contract

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`
- Test: `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationServiceTest.java`

- [ ] **Step 1: Write failing backend prompt test**

Add a test that sends a command with `planMode=true`, captures the system prompt passed to the fake AI client, and asserts it contains desktop goal mode and `get_goal` / `create_goal` / `update_goal` guidance.

Run: `mvn -Dtest=ChatApplicationServiceTest#planModeShouldInjectDesktopGoalToolGuidance test`
Expected: FAIL because the current prompt still says CLI Plan mode and lacks the complete goal tool guidance.

- [ ] **Step 2: Update plan mode context**

Rewrite `buildPlanModeContext` to describe “规划/目标模式” from CLI or desktop, require `get_goal` before creating/updating goals when appropriate, and preserve the no-side-effect planning boundary when the user only asks for a plan.

- [ ] **Step 3: Verify backend focused test**

Run: `mvn -Dtest=ChatApplicationServiceTest#planModeShouldInjectDesktopGoalToolGuidance test`
Expected: PASS.

### Task 4: Feature Documentation

**Files:**
- Create: `docs/features/chat/desktop-goal-mode.md`
- Modify: `docs/features/index.md`

- [ ] **Step 1: Document current behavior**

Create the feature document with purpose, entry, request flow, progress source, backend prompt boundary, key files, and validation commands.

- [ ] **Step 2: Update feature index**

Add `聊天桌面目标模式` under Chat in `docs/features/index.md`.

### Task 5: Full Verification and Review

**Files:**
- Read-only verification across changed frontend/backend files.

- [ ] **Step 1: Run frontend verification**

Run in `frontend/user`: `npm run build` and `npm run test:run`.
Expected: both exit 0, unless unrelated pre-existing tests fail; unrelated failures must be reported without modifying other-session code.

- [ ] **Step 2: Run backend verification**

Run in `backend`: `mvn compile` and `mvn test`.
Expected: both exit 0, unless unrelated pre-existing tests fail; unrelated failures must be reported without modifying other-session code.

- [ ] **Step 3: Run browser verification**

Start the backend and user frontend after checking ports 5001 and 5002. Use CDP/browser tooling to open `http://localhost:5002`, verify the target button and progress panel styling in light/dark mode, and save evidence under `logs/`.

- [ ] **Step 4: Review and commit**

Review `git diff` for only this feature's files, stage only those files, and commit with Chinese message `feat(chat): 增加桌面目标模式进度跟进`.
