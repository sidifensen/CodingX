# Chat User Process Timeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把聊天主区的思考、搜索、MCP 过程改为纯用户态时间轴，隐藏原始调用细节并保留独立最终回答。

**Architecture:** 在消息模型上新增统一过程节点数组，由 `useChatWorkspace` 把现有 SSE 事件映射为短句时间轴节点；`ChatView` 用新的过程时间轴组件替代原思考面板、MCP 面板和搜索大面板的默认用户态渲染。底层 `thinkingContent`、`mcpCalls`、`searchProgress` 仍保留为兼容输入来源，但不再作为默认主区 UI 直接暴露。

**Tech Stack:** React, TypeScript, Vitest, Testing Library, existing chat SSE aggregation utilities.

---

### Task 1: 补齐时间轴模型的失败测试

**Files:**
- Modify: `frontend/user/src/views/chat/useChatWorkspace.test.ts`
- Test: `frontend/user/src/views/chat/useChatWorkspace.test.ts`

- [ ] **Step 1: 编写失败测试，覆盖 thinking/search/mcp 事件映射为用户态过程节点**
- [ ] **Step 2: 运行 `cd frontend/user && npm run test:run -- src/views/chat/useChatWorkspace.test.ts`，确认新断言先失败**
- [ ] **Step 3: 只实现最小类型与聚合逻辑让测试转绿**
- [ ] **Step 4: 再次运行 `cd frontend/user && npm run test:run -- src/views/chat/useChatWorkspace.test.ts`，确认通过**

### Task 2: 补齐 ChatView 的失败测试

**Files:**
- Modify: `frontend/user/src/views/ChatView.test.tsx`
- Test: `frontend/user/src/views/ChatView.test.tsx`

- [ ] **Step 1: 编写失败测试，断言页面展示过程时间轴并隐藏 “MCP 调用 / 参数 / 原始结果” 文案**
- [ ] **Step 2: 运行 `cd frontend/user && npm run test:run -- src/views/ChatView.test.tsx`，确认测试先失败**
- [ ] **Step 3: 只实现最小视图改造让断言通过**
- [ ] **Step 4: 再次运行 `cd frontend/user && npm run test:run -- src/views/ChatView.test.tsx`，确认通过**

### Task 3: 实现消息模型与 Hook 聚合改造

**Files:**
- Modify: `frontend/user/src/views/chat/types.ts`
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`
- Test: `frontend/user/src/views/chat/useChatWorkspace.test.ts`

- [ ] **Step 1: 在 `types.ts` 新增过程节点类型，并给 `ChatMessageItem` 增加过程时间轴字段**
- [ ] **Step 2: 在 `useChatWorkspace.ts` 增加过程节点 upsert 与状态收敛辅助函数**
- [ ] **Step 3: 把 `thinking`、`step`、`reference`、`mcp-call`、`finish` 事件映射到统一过程节点**
- [ ] **Step 4: 运行 `cd frontend/user && npm run test:run -- src/views/chat/useChatWorkspace.test.ts`，确认相关测试通过**

### Task 4: 实现 ChatView 用户态时间轴

**Files:**
- Modify: `frontend/user/src/views/ChatView.tsx`
- Test: `frontend/user/src/views/ChatView.test.tsx`

- [ ] **Step 1: 新增用户态 `ProcessTimeline` 组件或同文件局部组件，并按现有主题令牌渲染运行中/完成/错误节点**
- [ ] **Step 2: 助手消息主区改为优先渲染 `processTimeline`，移除旧思考/MCP/搜索大面板的默认显示入口**
- [ ] **Step 3: 保持最终 Markdown 回答、附件与操作区渲染顺序稳定**
- [ ] **Step 4: 运行 `cd frontend/user && npm run test:run -- src/views/ChatView.test.tsx`，确认通过**

### Task 5: 全量验证与收尾

**Files:**
- Modify: `docs/superpowers/specs/2026-05-22-155051-chat-user-process-timeline-design.md`
- Modify: `docs/superpowers/acceptance/2026-05-22-155051-chat-user-process-timeline-acceptance.md`
- Modify: `docs/superpowers/plans/2026-05-22-155051-chat-user-process-timeline.md`
- Modify: `docs/superpowers/memory/index.md`
- Modify: `docs/superpowers/memory/module-chat-runtime-rendering.md`
- Modify: `docs/superpowers/memory/contract-chat-stream-display.md`
- Modify: `docs/superpowers/memory/reports/2026-05-22-155051-chat-user-process-timeline-bootstrap.md`

- [ ] **Step 1: 运行 `cd frontend/user && npm run test:run -- src/views/ChatView.test.tsx src/views/chat/useChatWorkspace.test.ts`**
- [ ] **Step 2: 运行 `cd frontend/user && npm run build`**
- [ ] **Step 3: 自检注释、暗色主题与无原始调用细节泄漏**
- [ ] **Step 4: 整理 diff，准备按仓库规范提交**
