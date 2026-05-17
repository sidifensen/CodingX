# 管理端技能页新增编辑能力 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复管理端技能页“新增不可用、无编辑入口”问题，完成可用弹窗 CRUD（本次包含新增+编辑）。

**Architecture:** 复用管理端现有 API (`AdminChatApi`) 与 MCP 页弹窗交互风格，在 `Skills.tsx` 内新增弹窗表单状态与提交逻辑。

**Tech Stack:** React、TypeScript、Vitest、Testing Library。

---

### Task 1: 先写失败测试

**Files:**
- Modify: `frontend/admin/src/pages/Skills.test.tsx`

- [ ] **Step 1: 新增创建弹窗测试**

断言：点击“创建新技能”会打开弹窗，提交后调用 `createSkill` 并刷新列表。

- [ ] **Step 2: 新增编辑弹窗测试**

断言：点击“编辑技能 xxx”会打开弹窗，提交后调用 `updateSkill` 并刷新列表。

- [ ] **Step 3: 运行定向测试（预期失败）**

Run: `cd frontend/admin && npm run test:run -- src/pages/Skills.test.tsx`
Expected: FAIL（当前页面无新增/编辑交互）。

### Task 2: 实现新增与编辑弹窗

**Files:**
- Modify: `frontend/admin/src/pages/Skills.tsx`

- [ ] **Step 1: 增加页面状态与入口按钮行为**

新增 `dialogOpen/dialogMode/editingSkill` 等状态；接通“创建新技能”按钮点击。

- [ ] **Step 2: 新增卡片编辑入口**

每个技能卡片增加“编辑”按钮，并带可访问名称 `编辑技能 {skillCode}`。

- [ ] **Step 3: 实现弹窗表单与提交逻辑**

实现 `SkillEditDialog`、必填校验、create/update 提交，成功后刷新列表。

- [ ] **Step 4: 完成错误提示与注释**

表单提交失败展示后端报错；关键流程补充业务意图注释。

### Task 3: 验证

**Files:**
- Verify only

- [ ] **Step 1: 运行测试**

Run:
1. `cd frontend/admin && npm run test:run -- src/pages/Skills.test.tsx`
2. `cd frontend/admin && npm run test:run`

Expected: PASS。

- [ ] **Step 2: 运行构建**

Run: `cd frontend/admin && npm run build`
Expected: PASS。

- [ ] **Step 3: CDP 页面验证并留证**

路径：`http://localhost:5003/skills` → 点击“创建新技能”/“编辑技能 xxx” → 提交。
证据：保存截图到 `logs/`。
