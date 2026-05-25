# 会话菜单与消息操作 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为用户端聊天页补齐会话菜单、批量管理、导出、置顶以及消息底部分享/重新生成操作

**Architecture:** 侧栏交互集中在 `Sidebar`，业务动作集中在 `useChatWorkspace`，本地持久化继续复用 `localConversationStorage`。后端已有动作直接复用接口，不新增后端契约；新增的置顶与导出逻辑以前端本地快照编排实现。

**Tech Stack:** React 19, TypeScript, Testing Library, Vitest, localStorage

---

### Task 1: 锁定菜单与批量模式行为

**Files:**
- Modify: `frontend/user/tests/components/Sidebar.test.tsx`
- Modify: `frontend/user/tests/App.test.tsx`

- [ ] 增加会话菜单完整项测试
- [ ] 增加导出子菜单测试
- [ ] 增加批量管理进入态测试

### Task 2: 扩展工作区控制器能力

**Files:**
- Modify: `frontend/user/src/views/chat/types.ts`
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`
- Modify: `frontend/user/src/views/chat/localConversationStorage.ts`

- [ ] 增加会话作用域动作类型
- [ ] 增加置顶、导出、批量删除动作接口
- [ ] 在本地快照中加入置顶持久化字段
- [ ] 实现单会话与批量导出
- [ ] 实现分组上下文下的删除与置顶更新

### Task 3: 改造侧栏菜单与批量交互

**Files:**
- Modify: `frontend/user/src/components/Sidebar.tsx`
- Modify: `frontend/user/src/App.tsx`

- [ ] 扩展侧栏菜单项
- [ ] 增加导出子菜单
- [ ] 增加批量管理模式与工具条
- [ ] 打通 App 到工作区控制器的动作透传

### Task 4: 验证消息底部操作

**Files:**
- Modify: `frontend/user/tests/views/ChatView.test.tsx`
- Modify: `frontend/user/src/views/ChatView.tsx`

- [ ] 确认分享与重新生成按钮测试覆盖
- [ ] 仅在必要时补齐主消息区交互细节

### Task 5: 验证与浏览器联调

**Files:**
- Modify: `frontend/user/tests/components/Sidebar.test.tsx`
- Modify: `frontend/user/tests/App.test.tsx`
- Modify: `frontend/user/tests/views/ChatView.test.tsx`

- [ ] 运行相关 Vitest 用例
- [ ] 执行 `npm run test:run`
- [ ] 执行 `npm run build`
- [ ] 启动前端并在浏览器中手动验证
