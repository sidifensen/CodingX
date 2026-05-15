# 聊天侧边栏会话归位 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把真实会话列表收敛到左侧 Sidebar，并让“新建对话”真正回到首页空态且走新会话链路。

**Architecture:** 将 `useChatWorkspace` 上提到 `App`，由 `App` 同时驱动 `Sidebar` 和 `ChatView`。`Sidebar` 负责展示真实会话列表与发起新建动作，`ChatView` 只负责主消息区与右侧回放区；新建态通过清空 `activeConversationId` 和工作台状态实现。

**Tech Stack:** React 19、TypeScript、Vitest、Testing Library、现有 `/api/chat/*` HTTP/SSE 接口

---

### Task 1: 用失败测试锁定左侧真实会话与新建行为

**Files:**
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/views/ChatView.test.tsx`

- [ ] **Step 1: 在 `App.test.tsx` 新增“侧边栏使用真实会话替代假数据”的失败测试**

测试要点：

- 预置本地登录态。
- mock `/api/chat/conversations` 返回真实会话。
- 断言左侧出现真实标题。
- 断言旧假数据标题不存在。

- [ ] **Step 2: 运行定向测试并确认失败**

Run: `npm run test:run -- src/App.test.tsx`

Expected: 失败，原因是当前 Sidebar 仍渲染假数据。

- [ ] **Step 3: 在 `App.test.tsx` 新增“点击新建后回到欢迎页且下次发送不带旧 conversationId”的失败测试**

测试要点：

- 先让应用载入一个真实会话与其消息。
- 点击“新建对话”。
- 断言欢迎页标题出现。
- 再次发送消息，断言 `/api/chat/stream` 请求 URL 不含 `conversationId=2001`。

- [ ] **Step 4: 在 `ChatView.test.tsx` 新增“聊天页不再渲染内部 Conversations 栏”的失败测试**

Run: `npm run test:run -- src/views/ChatView.test.tsx`

Expected: 失败，原因是 `ChatView` 目前仍包含内部 Conversations 左栏。

### Task 2: 上提聊天工作台状态并暴露 Sidebar 所需动作

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/views/chat/useChatWorkspace.ts`
- Modify: `frontend/src/views/chat/types.ts`

- [ ] **Step 1: 在 `useChatWorkspace` 增加 `startNewConversation` 与可复用状态类型**

实现要点：

- 新建态清空 `activeConversationId`、消息、步骤、来源、产物、错误与输入。
- 保留真实 `conversations` 列表。
- 如当前正在流式输出，优先中止请求并在可取消时调用取消接口。

- [ ] **Step 2: 修正新建态发送消息的流式 URL 生成逻辑**

实现要点：

- 有会话 ID 时拼接 `conversationId`。
- 无会话 ID 时不传该参数。

- [ ] **Step 3: 将 `useChatWorkspace` 从 `ChatView` 上提到 `App`**

实现要点：

- `App` 创建聊天工作台状态。
- `Sidebar` 与 `ChatView` 都改为消费 `App` 传入的数据与动作。

- [ ] **Step 4: 运行定向测试并确认 Task 1 中的相关失败开始转绿**

Run: `npm run test:run -- src/App.test.tsx src/views/ChatView.test.tsx`

Expected: 至少有部分断言转为通过，剩余失败聚焦在 UI 结构调整。

### Task 3: 重构 Sidebar 为真实会话列表

**Files:**
- Modify: `frontend/src/components/Sidebar.tsx`
- Create or Modify: `frontend/src/views/chat/types.ts`

- [ ] **Step 1: 删除 Sidebar 中硬编码的假数据历史列表**

- [ ] **Step 2: 基于真实会话列表渲染 Sidebar 分组**

实现要点：

- 分成“今天 / 最近七天 / 更早”。
- 解析失败的时间统一落入“更早”。
- 高亮当前选中会话。

- [ ] **Step 3: 让 Sidebar 的“新建对话”和会话点击都驱动聊天状态**

实现要点：

- “新建对话”切回 `chat` 视图并调用 `startNewConversation`。
- 点击会话时切回 `chat` 视图并调用 `selectConversation`。

- [ ] **Step 4: 为新增分组与空态补齐必要注释**

### Task 4: 简化 ChatView 布局为主区 + 回放区

**Files:**
- Modify: `frontend/src/views/ChatView.tsx`

- [ ] **Step 1: 删除 `ChatView` 内部的会话列表栏**

- [ ] **Step 2: 把空态首页改为欢迎页样式，并保留建议卡片回填能力**

- [ ] **Step 3: 保持右侧执行回放栏与消息流式展示逻辑可用**

- [ ] **Step 4: 为新的空态结构与关键布局补齐注释**

### Task 5: 验证、浏览器取证与收尾

**Files:**
- Modify: `logs/`（仅新增验证截图，不修改源码）

- [ ] **Step 1: 运行前端测试**

Run: `npm run test:run`

Expected: 全部通过。

- [ ] **Step 2: 运行前端构建**

Run: `npm run build`

Expected: 构建成功，退出码为 0。

- [ ] **Step 3: 启动前端并通过 CDP 验证真实会话、新建回首页与无假数据**

验证路径：

- 打开 `http://localhost:5002`
- 登录
- 进入聊天页
- 截图保存到 `logs/`

- [ ] **Step 4: 自检差异并提交中文 commit**

建议提交信息：`feat: 调整聊天侧边栏真实会话与新建流程`
