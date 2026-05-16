# 技能管理与斜杠技能选择对接增值接口设计

## 1. 背景与问题

当前仓库在“技能”能力上存在三处断层：

1. 管理端 `frontend/admin/src/pages/Skills.tsx` 仍使用 `mockSkills` 静态数据，未对接后端 `/api/admin/chat/skills`。
2. 用户端 `frontend/user/src/views/SkillsView.tsx` 全量静态渲染，未对接后端 `/api/chat/skills`。
3. 聊天输入框 `/` 仅触发 MCP 列表（`availableMcps`），无法检索/选择技能，导致“输入斜杠看不到技能”。

从后端实现看，`ChatSkill` 逻辑已复用 `chat_mcp` 物理表（`ChatSkillDO @TableName("chat_mcp")`），并暴露了管理端与用户端技能接口，因此“增值接口”本质上已存在，可直接作为前端统一数据源。

## 2. 目标

在不新增数据库结构的前提下，完成技能链路端到端对接：

1. 管理端技能管理页改为真实接口加载，展示真实技能列表。
2. 用户端技能库页改为真实接口加载，展示已安装技能与推荐技能。
3. 聊天输入 `/` 面板改为“技能 + MCP”联合候选，支持键盘选择并写入会话选择状态。
4. SSE 聊天入口支持显式 `skillCodes` 参数；若前端传了技能选择，则优先写入本次运行绑定，避免丢失技能上下文。

## 3. 非目标

1. 本次不新增技能市场后台管理能力（如“获取”按钮真实安装流程）。
2. 本次不重构后端命名（`mcpCodes` 字段沿用），以最小改动保证兼容。
3. 本次不改数据库表结构与迁移脚本。

## 4. 方案概述

### 4.1 管理端技能管理

- 将 `Skills.tsx` 从 `mockSkills` 切换为 `AdminChatApi.listSkills()`。
- 页面保留现有样式风格，增加最小状态管理：`loading / error / rows`。
- 将当前“版本/作者”静态位改为后端可用字段：`sourceType`、`category`、`sortNo` 等，避免继续展示伪字段。

### 4.2 用户端技能库

- 在用户侧 `ChatApi` 新增 `listSkills(token)`，请求 `/api/chat/skills`。
- `SkillsView` 在挂载后拉取真实技能，按 `enabled` 与 `category` 展示。
- 原静态“已安装(5)”改为实时数量；原推荐卡片保留视觉骨架，但由真实技能数据驱动。

### 4.3 聊天 `/` 选择技能

- 在 `ChatWorkspaceController` 增加：
  - `availableSkills`
  - `selectedSkillCodes`
  - `setSelectedSkillCodes`
- `useChatWorkspace` 启动时并行拉取 skills + mcps；默认全选启用技能，保持历史行为（之前是默认全选 MCP）。
- `ChatView` 把 slash 面板抽象为“命令候选”：
  - `type=skill|mcp`
  - 支持按名称/编码过滤
  - 回车/点击时，根据类型写入 `selectedSkillCodes` 或 `selectedMcpCodes`
  - 输入提示文案改为“输入 / 选择技能或MCP”

### 4.4 后端 stream 参数兼容

- `ChatStreamController.streamChat` 新增可选参数 `skillCodes`。
- 解析优先级：
  - 若 `skillCodes` 显式传入：使用其去重结果。
  - 否则保持当前行为（从 `chat_mcp` 启用项回退）。
- 将解析结果继续传入现有 `SendChatMessageCommand`（字段名 `mcpCodes` 暂不改，避免影响面）。

## 5. 错误处理与兼容性

1. 前端接口层统一继续使用 `ApiResponseParser`，页面不直接 `response.json()`。
2. 用户端技能接口失败时：
   - 技能页显示错误态；
   - 聊天页 `/` 面板仍可回退到 MCP 候选，不阻断发消息。
3. 后端新增参数为可选，不影响既有调用。

## 6. 测试策略（TDD）

### 6.1 前端

1. `frontend/admin/src/pages/Skills.test.tsx`
   - 先断言调用 `AdminChatApi.listSkills` 并渲染真实数据。
2. `frontend/user/src/views/SkillsView.test.tsx`
   - 先断言调用用户侧技能 API 并渲染后端返回技能。
3. `frontend/user/src/views/ChatView.test.tsx`
   - 先增加 `/` 面板展示技能候选、回车后写入 `selectedSkillCodes` 的失败测试。

### 6.2 后端

1. `ChatStreamControllerTest`
   - 先新增 `skillCodes` 显式传入时优先绑定该列表的失败测试。

## 7. 风险与缓解

1. 命名风险：`skill` 逻辑复用 `chat_mcp` 容易混淆。
   - 缓解：在代码注释明确“技能视图复用 MCP 物理表”。
2. UI 回归风险：slash 面板从单类型扩展到双类型。
   - 缓解：保持现有键盘逻辑，新增类型标签并补测试。
3. 在途改动冲突风险：仓库已有未提交修改。
   - 缓解：仅改动技能相关文件，不触碰 query-term 等在途模块。

## 8. 交付清单

1. 管理端技能页接真实接口。
2. 用户端技能库页接真实接口。
3. 聊天 `/` 面板支持技能候选与选择。
4. 后端 stream 接口支持 `skillCodes`。
5. 对应前后端测试通过。
