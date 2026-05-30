# 管理端反馈管理 Ant Design 改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将管理端反馈列表、详情、引用来源三页迁移到 Ant Design 组件，并保持现有接口与业务行为不变。

**Architecture:** 新增一个管理端 Ant Design 主题桥接组件，在应用根部映射现有 CSS 变量到 Ant Design token。反馈三页只替换呈现层，继续通过 `AdminChatApi` 获取数据。测试先定义 Ant Design 迁移后的用户可见行为，再实现组件替换。

**Tech Stack:** React、TypeScript、Ant Design、Vitest、Testing Library、VitePlus、Chrome DevTools CDP。

---

### Task 1: 依赖与主题桥接

**Files:**
- Modify: `frontend/admin/package.json`
- Modify: `frontend/admin/package-lock.json`
- Create: `frontend/admin/src/components/AdminAntdProvider.tsx`
- Modify: `frontend/admin/src/App.tsx`

- [ ] **Step 1: 安装 `antd` 依赖**

Run: `cd frontend/admin && npm install antd@^5`
Expected: `package.json` 和 `package-lock.json` 增加 `antd`。

- [ ] **Step 2: 创建 Ant Design 主题桥接组件**

Create `frontend/admin/src/components/AdminAntdProvider.tsx` with a component that:
- wraps children with `ConfigProvider`
- reads whether `document.documentElement` contains `dark`
- observes class changes through `MutationObserver`
- uses `theme.darkAlgorithm` when dark mode is enabled
- maps CSS variables such as `--theme-surface-container-lowest` and `--theme-ink` into token values

- [ ] **Step 3: 在 App 根部接入主题桥接**

Wrap existing router content in `<AdminAntdProvider>` without changing routes or auth logic.

- [ ] **Step 4: 运行构建验证依赖解析**

Run: `cd frontend/admin && npm run build`
Expected: build succeeds or fails only on later page migration type errors that will be fixed in Task 3.

### Task 2: 先写失败测试

**Files:**
- Modify: `frontend/admin/tests/pages/FeedbackPage.test.tsx`
- Modify: `frontend/admin/tests/pages/FeedbackDetailPage.test.tsx`
- Modify: `frontend/admin/tests/pages/FeedbackReferencePage.test.tsx`

- [ ] **Step 1: 更新列表页测试**

Add assertions for Ant Design table/list behavior:
- row renders feedback values
- detail link remains `/feedbacks/9001`
- vote filter submits `vote: -1`
- loading state is visible while Promise is pending
- API rejection displays backend error message

- [ ] **Step 2: 更新详情页测试**

Add assertions for:
- descriptions-style labels visible
- message content visible
- references link remains `/feedbacks/9001/references`
- API rejection displays backend error message

- [ ] **Step 3: 更新引用来源页测试**

Add assertions for:
- source row values visible
- source link href and target attributes remain safe
- API rejection displays backend error message

- [ ] **Step 4: 运行定向测试确认 RED**

Run: `cd frontend/admin && npm run test:run -- tests/pages/FeedbackPage.test.tsx tests/pages/FeedbackDetailPage.test.tsx tests/pages/FeedbackReferencePage.test.tsx`
Expected: tests fail because pages still use old custom markup or lack Ant Design loading/error semantics.

### Task 3: 迁移反馈三页到 Ant Design

**Files:**
- Modify: `frontend/admin/src/pages/FeedbackPage.tsx`
- Modify: `frontend/admin/src/pages/FeedbackDetailPage.tsx`
- Modify: `frontend/admin/src/pages/FeedbackReferencePage.tsx`

- [ ] **Step 1: 迁移反馈列表页**

Replace native controls and hand-written table with Ant Design `Form`, `Input`, `Select`, `Button`, `Table`, `Tag`, `Alert`, `Typography`, and `Space`. Keep `PAGE_SIZE = 10`, filter semantics, refresh behavior, date formatting and detail route unchanged.

- [ ] **Step 2: 迁移反馈详情页**

Use Ant Design `Card`, `Descriptions`, `Skeleton`, `Alert`, `Button`, `Typography`, and `Space`. Preserve empty value fallback, links, message whitespace, and error extraction.

- [ ] **Step 3: 迁移反馈引用来源页**

Use Ant Design `Card`, `Table`, `Alert`, `Button`, `Typography`, `Space`, and `Empty`. Preserve safe external link attributes and empty value fallback.

- [ ] **Step 4: 运行定向测试确认 GREEN**

Run: `cd frontend/admin && npm run test:run -- tests/pages/FeedbackPage.test.tsx tests/pages/FeedbackDetailPage.test.tsx tests/pages/FeedbackReferencePage.test.tsx`
Expected: all three test files pass.

### Task 4: 功能文档与回归验证

**Files:**
- Modify: `docs/features/index.md`
- Create: `docs/features/admin/feedback-management-antd.md`

- [ ] **Step 1: 编写功能文档**

Document the current implementation, entry points, core flow, key files and verification method.

- [ ] **Step 2: 执行管理端全量测试**

Run: `cd frontend/admin && npm run test:run`
Expected: all admin frontend tests pass.

- [ ] **Step 3: 执行管理端构建**

Run: `cd frontend/admin && npm run build`
Expected: build succeeds.

- [ ] **Step 4: CDP 浏览器验证**

Start admin dev server after checking port 5003. Open `http://localhost:5003/feedbacks`, verify light and dark theme table/filter/detail/reference readability, and save evidence under `logs/`.

- [ ] **Step 5: 提交本次改造**

Stage only files changed for this task and commit with Chinese message:
`feat(admin): 使用 Ant Design 改造反馈管理页面`
