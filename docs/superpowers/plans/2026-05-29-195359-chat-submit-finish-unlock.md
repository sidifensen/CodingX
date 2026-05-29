# Chat Submit Finish Unlock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复聊天流式 finish 已到达但后续回放未结束期间第三次发送被静默拦截、刷新后恢复到“正在生成回答...”，以及旧流慢回放覆盖本地快照导致上一轮消息刷新后消失的问题。

**Architecture:** 将“模型已完成输出”和“后台收尾回放”拆开处理：finish 到达后立即释放用户可提交状态，并把最终助手消息写入本地快照；如果用户已经开启更新一轮流，旧流慢回放不得再覆盖消息区和本地快照。保持既有 SSE、快照和会话切换结构不变。

**Tech Stack:** React hook、Vitest、Testing Library、SSE ReadableStream mock。

---

### Task 1: 回归测试

**Files:**
- Modify: `frontend/user/tests/views/chat/useChatWorkspace.submit.test.ts`

- [ ] **Step 1: 写失败测试**

新增测试覆盖：第二次请求收到 finish 后，`isStreaming` 已关闭但会话列表回放仍挂起，此时第三次 `submitMessage()` 应能发出新流请求，并且本地快照中当前会话助手消息不应保持 `streaming`。第三次请求已经启动后，第二次请求的慢回放返回也不得把第二次用户消息和助手回答从快照里覆盖掉。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.submit.test.ts`

Expected: 新测试失败，表现为第三次流请求未发出或快照仍包含 `status: "streaming"`。

### Task 2: 修复发送收尾状态

**Files:**
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`

- [ ] **Step 1: 实现最小修复**

在 finish 事件处理里完成三件事：
- 用真实 conversationId/messageId/content 更新内存消息；
- 立即将最终消息持久化到当前工作区快照；
- 释放 `activeStreamSessionIdRef` 和当前 abort controller，让下一次提交不再被内部锁拦截。
- 当旧流 finish 后发现已有更新流启动时，跳过旧流后续 `selectConversation` 回放，避免慢接口用旧数据覆盖新快照。
- 在旧流的慢会话列表请求返回后再次检查是否已有更新流启动，覆盖“请求期间用户发出第三条消息”的竞态。

- [ ] **Step 2: 保留边界**

仅释放当前 streamSession 对应的控制器，避免旧流事件影响新请求；保留 `streamStateRef` 的会话与消息标识，供后续回放合并面板字段。

### Task 3: 验证

**Files:**
- Run only

- [ ] **Step 1: 定向测试**

Run: `cd frontend/user && npm run test:run -- tests/views/chat/useChatWorkspace.submit.test.ts`

- [ ] **Step 2: 前端构建**

Run: `cd frontend/user && npm run build`

- [ ] **Step 3: 浏览器验证**

启动或复用 `http://localhost:5002`，通过 CDP 验证发送按钮在 finish 后可点击，截图保存到 `logs/`。
