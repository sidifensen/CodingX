# Chat Refresh Stream Detach Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复聊天生成中刷新页面被误判为“已停止当前生成”的问题，并确保刷新后能按后台任务续流恢复。

**Architecture:** 前端把用户显式停止和页面刷新、卸载、会话切换导致的本地 SSE 脱离区分开。刷新或本地脱离只清理当前浏览器连接，不把助手消息改成 cancelled，也不调用后端取消接口；收到 `meta.conversationId` 后同步把流式消息快照迁移到真实会话，保证刷新恢复有可回放记录。

**Tech Stack:** React hook、Vitest、Testing Library、CDP 浏览器验证。

---

### Task 1: 回归测试

**Files:**
- Modify: `frontend/user/tests/views/chat/useChatWorkspace.submit.test.ts`

- [ ] **Step 1: Write the failing test**

新增用例：提交消息后模拟后端发送 `meta` 和部分 `message`，再触发 `pagehide` 并让 reader 抛出 `AbortError`。断言 `streamError` 不是 `已停止当前生成`，助手消息仍是 `streaming`，本地快照在真实会话 `2001` 下保留流式消息。

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.submit.test.ts -t 刷新页面时应只脱离本地流`

Expected: FAIL，因为当前 `AbortError` 分支会把助手消息改成 `cancelled` 并设置停止提示。

### Task 2: 本地脱离语义

**Files:**
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`

- [ ] **Step 1: Implement minimal fix**

增加本地流中断原因记录。`pagehide`、hook unmount、切换会话、新建会话、切换运行环境、重置工作台等只标记为 detach；用户点击停止继续保留 cancelled 语义。`AbortError` catch 只在非 detach 时展示停止提示。

- [ ] **Step 2: Persist real conversation stream snapshot**

收到 `meta.conversationId` 后把当前 `messagesRef.current` 重新写入真实会话快照，避免刷新恢复只能看到空会话壳或旧 `pending-conversation` 快照。

- [ ] **Step 3: Run focused tests**

Run: `cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.submit.test.ts`

Expected: PASS。

### Task 3: 验证与文档

**Files:**
- Modify: `docs/features/chat/background-stream-resume.md`
- Modify: `docs/features/index.md` only if the feature index no longer references the document.

- [ ] **Step 1: Update feature documentation**

记录刷新、卸载、会话切换均属于本地订阅脱离，只有显式停止会取消后端后台任务。

- [ ] **Step 2: Run build and diff checks**

Run: `cd frontend/user && npm run build`

Run: `git diff --check -- frontend/user/src/views/chat/useChatWorkspace.ts frontend/user/tests/views/chat/useChatWorkspace.submit.test.ts docs/superpowers/plans/2026-05-30-224935-chat-refresh-stream-detach.md docs/features/chat/background-stream-resume.md`

Expected: build exit 0, diff check exit 0。

- [ ] **Step 3: Browser validation**

用 CDP 打开 `http://localhost:5002`，在生成过程中刷新页面，确认没有红色“已停止当前生成”提示，运行中会话仍能恢复续流。截图保存到 `logs/chat-refresh-running-fix.png`。

- [ ] **Step 4: Commit**

只暂存本次相关文件并提交：`fix(chat): 修复刷新中断误判停止生成`。
