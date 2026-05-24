# Chat Stream State Guard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复聊天首屏旧 URL 会话恢复请求晚于新提问返回时，主消息区被过期恢复任务清空，导致页面回到欢迎页但输入区仍显示生成中的问题。

**Architecture:** 在 `useChatWorkspace` 内补齐流式会话期间的状态保护：旧 bootstrap 恢复任务和会话列表刷新只能更新侧栏和快照，不得用发送前闭包里的空会话状态覆盖当前流式会话。修复通过前端 Hook 测试覆盖，避免再次出现 URL 有真实 `conversationId` 但主区显示首页的状态。

**Tech Stack:** React Hook、Vitest、Testing Library、Vite Plus。

---

### Task 1: Reproduce Stale URL Bootstrap Reset

**Files:**
- Modify: `frontend/user/tests/views/chat/useChatWorkspace.test.ts`

- [x] **Step 1: Write the failing test**

新增测试：URL 带旧 `conversationId` 进入页面，首轮会话列表请求挂起；用户此时发送新问题并收到 SSE `meta` 与正文片段；随后旧会话列表请求返回空数组时，主消息区仍应保留当前流式消息，不应回到欢迎页。

- [x] **Step 2: Run test to verify it fails**

Run: `npm run test:run -- tests/views/chat/useChatWorkspace.test.ts -t "发送中旧URL恢复失败不应清空当前流式消息"`

Expected: FAIL，`activeConversationId` 被回写为 `null`，`messages` 被清空，而 `isStreaming` 仍为 `true`。

### Task 2: Guard Conversation Refresh During Active Stream

**Files:**
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`

- [x] **Step 1: Implement minimal fix**

在 `bootstrapWorkspace` 和 `loadConversations` 中判断当前是否存在激活流式会话。若存在，跳过旧 URL 恢复任务的清空分支，并合并当前快照中的流式会话列表；不要用旧闭包里的 `activeConversationId/messages` 推导首页空态。

- [x] **Step 2: Run focused test**

Run: `npm run test:run -- tests/views/chat/useChatWorkspace.test.ts -t "发送中旧URL恢复失败不应清空当前流式消息"`

Expected: PASS。

- [x] **Step 3: Run chat workspace tests**

Run: `npm run test:run -- tests/views/chat/useChatWorkspace.test.ts tests/views/chat/useChatWorkspace.submit.test.ts tests/views/chat/chatApi.test.ts tests/views/chat/sse.test.ts`

Expected: PASS。

### Task 3: Frontend Verification

**Files:**
- No production file beyond Task 2.

- [x] **Step 1: Run frontend build**

Run: `npm run build`

Expected: exit 0。

- [x] **Step 2: Review git diff**

确认只包含计划、前端测试、前端 Hook 修复，不包含无关改动。
