# 项目画像与长期记忆

## 功能用途

项目画像与长期记忆用于让 Agent 在执行项目任务前获得稳定上下文。项目画像负责扫描本地工作空间并沉淀模块地图、测试命令、关键入口、风险点和 Agent 输入摘要；长期记忆负责从明确“记住/长期保存/以后都按”等用户表达中提取候选，经用户或管理员确认后再回注到后续聊天上下文。

## 使用入口

- 用户端聊天页：本地工作空间绑定后，输入区下方显示“工作区智能”信息条，展示项目画像摘要、测试命令、风险摘要和待确认记忆。
- 用户端记忆接口：`GET /api/chat/memories` 查询当前用户可见记忆，`PATCH /api/chat/memories/{memoryId}/status` 确认或拒绝候选。
- 管理端治理中心：进入「治理中心」，在「项目画像」页签查看模块地图、测试命令、关键入口、风险点和 Agent 上下文，在「长期记忆」页签审核候选记忆。
- 管理端治理接口：`GET /api/admin/governance/long-term-memories` 和 `PATCH /api/admin/governance/long-term-memories/{id}/status` 管理长期记忆。

## 核心流程

1. 用户端选择或切换本地仓库目录后，`useHostContext` 调用 `ChatApi.bindWorkspaceRepository`，后端 `ChatWorkspaceBindingService` 校验目录、创建或复用本地工作空间，并调用 `ProjectProfileService.scanWorkspace` 生成画像。扫描结果写入 `governance_project_profile`，绑定响应返回 `workspaceId`、`projectProfile` 和 `pendingMemoryCount`，前端同步到 `useChatWorkspace` 状态。
2. 聊天提交时，`ChatApplicationService` 在构造模型历史前调用 `GovernanceAgentContextService`。该服务按当前用户、工作空间和问题读取最新项目画像与 ACTIVE 长期记忆，生成一段带使用约束的上下文，插入模型历史；画像或记忆缺失时返回空文本，不阻断聊天。
3. 助手正常完成后，`GovernanceAgentContextService.extractMemoryCandidates` 委托 `LongTermMemoryService` 读取用户消息。只有文本包含“记住”“请记忆”“长期保存”“以后都按”“我的偏好”等显式信号时才生成 PENDING 记忆，并按范围、用户、工作空间和内容生成确定性去重键；重复候选不会再次保存。
4. 用户端“工作区智能”信息条只展示当前工作空间记忆候选，用户点击确认按钮后调用 `PATCH /api/chat/memories/{memoryId}/status` 将状态改为 `ACTIVE`，点击拒绝则改为 `REJECTED`。更新完成后前端刷新记忆列表和待确认数量；接口失败时展示后端 `ApiResponse.message`。
5. 管理端治理中心并行加载权限、Hook、项目画像、长期记忆、Slash Command 和审计数据。管理员在「长期记忆」页签确认或拒绝任意候选，页面使用 Ant Design 按钮和消息组件，不使用浏览器原生弹窗。

## 关键文件

- `backend/src/main/java/com/codingx/governance/application/service/ProjectProfileService.java`：扫描工作空间并生成画像 JSON 与 Agent 上下文。
- `backend/src/main/java/com/codingx/governance/application/service/LongTermMemoryService.java`：提取、去重、确认、拒绝和检索长期记忆。
- `backend/src/main/java/com/codingx/governance/application/service/GovernanceAgentContextService.java`：组合项目画像与 ACTIVE 记忆并回注聊天上下文。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatWorkspaceBindingService.java`：目录绑定时刷新画像并返回待确认记忆数量。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：保存画像、记忆列表和状态更新动作。
- `frontend/user/src/views/ChatView.tsx`：渲染“工作区智能”信息条和候选确认按钮。
- `frontend/admin/src/pages/GovernanceCenterPage.tsx`：管理端项目画像增强列和长期记忆页签。

## 关键数据结构

- `governance_project_profile.module_map_json`：模块地图 JSON，记录后端、用户端、管理端等模块。
- `governance_project_profile.test_commands_json`：按模块推断的测试或构建命令。
- `governance_project_profile.key_entrypoints_json`：启动类、前端入口、控制器和配置文件等关键入口。
- `governance_project_profile.risk_points_json`：扫描发现的风险点或验证缺口。
- `governance_project_profile.agent_context`：供模型输入的项目级上下文摘要。
- `governance_long_term_memory`：长期记忆表，状态为 `PENDING`、`ACTIVE` 或 `REJECTED`，范围为 `USER` 或 `PROJECT`。

## 测试与验证

- 后端测试覆盖画像字段、迁移/基线结构、记忆提取去重、状态更新、检索、聊天上下文回注和用户/管理端记忆接口。
- 用户端测试覆盖目录绑定响应、长期记忆 API、`useChatWorkspace` 状态同步和“工作区智能”确认交互。
- 管理端测试覆盖治理中心长期记忆页签、项目画像增强字段和确认操作。
- 前端页面改动需要通过浏览器/CDP 打开用户端聊天页和管理端治理中心，保存截图或计算样式证据到 `logs/`。
