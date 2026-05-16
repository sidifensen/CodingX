# 技能管理与斜杠技能选择对接增值接口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 管理端与用户端技能页接入真实技能接口，并让聊天输入 `/` 同时支持技能与 MCP 选择，补齐后端 stream 的 `skillCodes` 兼容。

**Architecture:** 复用现有 `ChatSkill` 后端接口（物理表 `chat_mcp`），在前端新增技能数据状态并扩展 slash 候选模型；后端仅增加可选参数解析，不做破坏性重构。

**Tech Stack:** Spring Boot、MyBatis-Plus、React、TypeScript、Vitest、Testing Library。

---

### Task 1: 先写失败测试（管理端技能页）

**Files:**
- Create: `frontend/admin/src/pages/Skills.test.tsx`
- Modify: `frontend/admin/src/pages/Skills.tsx`

- [ ] **Step 1: 新建管理端技能页测试**

测试点：
1. 页面加载会调用 `AdminChatApi.listSkills`。
2. 渲染接口返回的技能名称/编码/分类，不再依赖 `mockSkills`。

- [ ] **Step 2: 运行定向测试（预期失败）**

Run: `cd frontend/admin && npm run test:run -- Skills.test.tsx`
Expected: FAIL（当前 `Skills.tsx` 仍依赖 `mockSkills`）。

### Task 2: 实现管理端技能页真实对接

**Files:**
- Modify: `frontend/admin/src/pages/Skills.tsx`

- [ ] **Step 1: 改为接口驱动状态**

新增 `loading/error/skills` 状态，挂载时调用 `AdminChatApi.listSkills()`。

- [ ] **Step 2: 替换静态字段展示**

以 `displayName/skillCode/category/sourceType/enabled/sortNo` 渲染卡片，不再展示伪 `version/author`。

- [ ] **Step 3: 补充注释并保证夜间模式兼容**

在关键状态分支增加业务意图注释，继续复用现有主题令牌类。

- [ ] **Step 4: 运行测试转绿**

Run: `cd frontend/admin && npm run test:run -- Skills.test.tsx`
Expected: PASS。

### Task 3: 先写失败测试（用户端技能库）

**Files:**
- Create: `frontend/user/src/views/SkillsView.test.tsx`
- Modify: `frontend/user/src/views/chat/chatApi.ts`

- [ ] **Step 1: 新建技能库页测试**

测试点：
1. 挂载后调用技能接口。
2. “已安装”数量与接口返回一致。
3. 渲染真实技能名称与描述。

- [ ] **Step 2: 运行定向测试（预期失败）**

Run: `cd frontend/user && npm run test:run -- SkillsView.test.tsx`
Expected: FAIL（当前 `SkillsView.tsx` 为静态列表）。

### Task 4: 实现用户端技能库真实对接

**Files:**
- Modify: `frontend/user/src/views/SkillsView.tsx`
- Modify: `frontend/user/src/views/chat/chatApi.ts`
- Modify: `frontend/user/src/views/chat/types.ts`

- [ ] **Step 1: 在 ChatApi 增加 listSkills**

新增 `listSkills(token)`，请求 `/api/chat/skills` 并统一解析。

- [ ] **Step 2: SkillsView 使用接口数据**

挂载时读取 token 并请求技能数据，渲染加载态/错误态/空态/成功态。

- [ ] **Step 3: 保持页面风格与双主题**

仅替换数据来源，不破坏现有亮暗主题类与视觉结构。

- [ ] **Step 4: 运行测试转绿**

Run: `cd frontend/user && npm run test:run -- SkillsView.test.tsx`
Expected: PASS。

### Task 5: 先写失败测试（聊天 `/` 技能候选）

**Files:**
- Modify: `frontend/user/src/views/ChatView.test.tsx`
- Modify: `frontend/user/src/views/chat/types.ts`

- [ ] **Step 1: 增加 slash 技能候选测试**

测试点：
1. 输入 `/` 时出现技能候选。
2. 回车选中技能后调用 `setSelectedSkillCodes`。

- [ ] **Step 2: 运行定向测试（预期失败）**

Run: `cd frontend/user && npm run test:run -- ChatView.test.tsx`
Expected: FAIL（当前 slash 仅支持 MCP）。

### Task 6: 实现聊天 `/` 技能 + MCP 联合候选

**Files:**
- Modify: `frontend/user/src/views/chat/types.ts`
- Modify: `frontend/user/src/views/chat/chatApi.ts`
- Modify: `frontend/user/src/views/chat/useChatWorkspace.ts`
- Modify: `frontend/user/src/views/ChatView.tsx`

- [ ] **Step 1: 扩展工作区状态模型**

新增 `availableSkills/selectedSkillCodes/setSelectedSkillCodes`。

- [ ] **Step 2: 启动阶段并行拉取技能与 MCP**

`bootstrapWorkspace` 并行调用 `listSkills + listMcps`，分别写入状态。

- [ ] **Step 3: slash 面板支持类型化候选**

候选结构增加 `kind` 字段（`skill` / `mcp`），选择时按类型写入对应状态。

- [ ] **Step 4: 调整文案与可访问属性**

输入框 placeholder 与候选标签更新为“技能/MCP”联合语义，并补测试标识。

- [ ] **Step 5: 运行测试转绿**

Run: `cd frontend/user && npm run test:run -- ChatView.test.tsx chatApi.test.ts SkillsView.test.tsx`
Expected: PASS。

### Task 7: 先写失败测试（后端 stream skillCodes 优先）

**Files:**
- Modify: `backend/src/test/java/com/codingx/chat/interfaces/controller/ChatStreamControllerTest.java`

- [ ] **Step 1: 新增显式 skillCodes 用例**

断言 `streamChat(..., skillCodes=...)` 时，派发命令与 meta 中使用显式值。

- [ ] **Step 2: 运行定向测试（预期失败）**

Run: `cd backend && mvn test -Dtest=ChatStreamControllerTest`
Expected: FAIL（当前接口无 `skillCodes` 参数）。

### Task 8: 实现后端 stream skillCodes 参数兼容

**Files:**
- Modify: `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatStreamController.java`

- [ ] **Step 1: 扩展 stream 参数签名**

新增可选 `skillCodes` 参数。

- [ ] **Step 2: 增加解析优先级**

显式 `skillCodes` 优先；未传保持旧回退逻辑。

- [ ] **Step 3: 补充注释与兼容调用**

注释说明兼容意图，并保证旧方法重载行为不变。

- [ ] **Step 4: 运行测试转绿**

Run: `cd backend && mvn test -Dtest=ChatStreamControllerTest,ChatSkillControllerTest,AdminChatSkillControllerTest`
Expected: PASS。

### Task 9: 全量验证与提交

**Files:**
- Verify only

- [ ] **Step 1: 前端验证**

Run:
1. `cd frontend/admin && npm run test:run -- Skills.test.tsx`
2. `cd frontend/admin && npm run build`
3. `cd frontend/user && npm run test:run -- ChatView.test.tsx SkillsView.test.tsx chatApi.test.ts`
4. `cd frontend/user && npm run build`

Expected: PASS。

- [ ] **Step 2: 后端验证**

Run:
1. `cd backend && mvn test -Dtest=ChatStreamControllerTest,ChatSkillControllerTest,AdminChatSkillControllerTest`
2. `cd backend && mvn compile`

Expected: PASS。

- [ ] **Step 3: 提交代码**

Run:
1. `git add <本次改动文件>`
2. `git commit -m "feat: 技能管理与斜杠技能选择对接真实接口"`
