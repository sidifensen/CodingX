# 专家中心设计

## 背景

当前用户端的专家页是静态页面，聊天输入区只支持技能和 MCP，不支持专家角色切换。用户希望先落地单个专家模式：在专家页或聊天输入区选择专家后，后续消息自动注入专家提示词，并默认填入该专家的示例问题。管理端还需要提供专家配置表格页面，并内置 20 条专家数据。

## 目标

1. 用户端可浏览真实专家列表，并从专家页或聊天输入区切换当前专家。
2. 当前专家为消息级参数 `expertCode`，同一会话中可随时切换，不强绑定 `conversationId`。
3. 后端在聊天模型 system prompt 中注入专家提示词，并将本次运行绑定专家持久化，支持回放。
4. 管理端提供专家表格 CRUD 页面，风格与现有技能/MCP 管理页一致。
5. 数据库新增专家表与任务专家绑定表，并初始化 20 条中文专家数据。

## 方案

### 数据模型

- 新增 `expert` 表，字段包含：
  - `expert_code`
  - `display_name`
  - `description`
  - `category`
  - `tags_json`
  - `avatar_url`
  - `preset_question`
  - `system_prompt`
  - `enabled`
  - `sort_no`
- 新增 `task_expert` 表，将每次聊天运行 `task_id/run_id` 绑定到一个 `expert_code`。

### 后端链路

- `/api/chat/stream` 接收可选 `expertCode`。
- `SendChatMessageCommand` 增加 `expertCode`。
- `ChatStreamExecutionService` 在运行开始时写入 `task_expert`。
- `ChatApplicationService.buildAiHistory(...)` 按顺序拼装：
  - system intent prompt
  - expert system prompt
  - skill context
- 新增用户侧 `/api/chat/experts` 与管理侧 `/api/admin/experts`。
- 聊天工作区增加 `current-experts` 查询接口，用于右栏回放。

### 用户端

- `useChatWorkspace` 维护：
  - `availableExperts`
  - `selectedExpertCode`
  - `currentExperts`
- 专家页改为真实数据列表，可搜索、按分类过滤、点击后切回聊天页并填入 `presetQuestion`。
- 聊天输入区在“技能”旁新增“专家”按钮，共用下拉面板样式。

### 管理端

- 新增专家管理路由与侧栏入口。
- 页面使用 `DataTableCard`。
- 支持分页、创建、编辑、删除。

## 边界与取舍

- 本期不实现“专家团”运行逻辑，只保留单个专家能力；专家团卡片仍可作为占位展示。
- 专家选择默认在当前聊天工作区持续生效，直到用户清空或切换为其他专家。
- 不把 `expertId` 写入会话主表，避免会话级锁死专家，满足“同一会话随时切专家”。
