# 项目画像与长期记忆

## 功能用途

项目画像与长期记忆用于让 Agent 在执行项目任务前获得稳定上下文。项目画像负责扫描本地工作空间并沉淀模块地图、测试命令、关键入口、风险点和 Agent 输入摘要；长期记忆负责从明确“记住/长期保存/以后都按”等用户授权表达中提取记忆，保存为 `ACTIVE` 后直接参与后续聊天上下文回注。

## 使用入口

- 用户端聊天页：本地工作空间绑定后，输入区下方显示“工作区智能”信息条，展示项目画像摘要、测试命令、风险摘要和已生效记忆。
- 用户端记忆管理页：侧栏点击「记忆管理」或直接访问 `/memories`，查看当前账号可见的用户记忆和项目记忆，并执行筛选、编辑、启用、停用和删除。
- 用户端记忆接口：`GET /api/chat/memories` 查询当前用户可见记忆，`PATCH /api/chat/memories/{memoryId}` 更新记忆正文，`PATCH /api/chat/memories/{memoryId}/status` 启用或停用已有记忆，`DELETE /api/chat/memories/{memoryId}` 逻辑删除记忆。
- 管理端治理中心：进入「治理中心」，在「项目画像」页签查看模块地图、测试命令、关键入口、风险点和 Agent 上下文，在「长期记忆」页签治理已提取记忆。
- 管理端治理接口：`GET /api/admin/governance/long-term-memories` 和 `PATCH /api/admin/governance/long-term-memories/{id}/status` 管理长期记忆启停状态。

## 核心流程

1. 用户端选择或切换本地仓库目录后，`useHostContext` 调用 `ChatApi.bindWorkspaceRepository`，后端 `ChatWorkspaceBindingService` 校验目录、创建或复用本地工作空间，并调用 `ProjectProfileService.scanWorkspace` 生成画像。扫描结果写入 `governance_project_profile`，绑定响应返回 `workspaceId`、`projectProfile` 和 `activeMemoryCount`，前端同步到 `useChatWorkspace` 状态。
2. 聊天提交时，`ChatApplicationService` 在构造模型历史前调用 `GovernanceAgentContextService`。该服务按当前用户、工作空间和问题读取最新项目画像与 ACTIVE 长期记忆，生成一段带使用约束的上下文，插入模型历史；画像或记忆缺失时返回空文本，不阻断聊天。
3. 助手正常完成后，`GovernanceAgentContextService.extractMemoryCandidates` 委托 `LongTermMemoryService` 读取用户消息。只有文本包含“记住”“请记忆”“长期保存”“以后都按”“我的偏好”等显式授权信号时才生成 `ACTIVE` 记忆，并按范围、用户、工作空间和内容生成确定性去重键；重复内容不会再次保存，普通聊天不会被自动沉淀为长期记忆。
4. 用户端“工作区智能”信息条展示当前工作空间的已生效记忆数量和最多 3 条摘要，不再弹出确认/拒绝按钮。用户刷新信息条时前端重新调用 `GET /api/chat/memories?status=ACTIVE`，接口失败时展示后端 `ApiResponse.message`。
5. 用户进入 `/memories` 后，`MemoryView` 读取登录令牌并调用 `ChatApi.listLongTermMemories(token, workspaceId, 'ALL')`，一次性加载当前用户可见的 ACTIVE 与 REJECTED 记忆。页面在本地按 `memoryScope` 和 `status` 筛选；编辑时先用自定义弹窗校验非空正文，再调用 `PATCH /api/chat/memories/{memoryId}`，后端校验当前用户归属并刷新 `content`、`keywordJson`、`memoryKey` 和 `updatedAt`；删除时通过自定义确认弹窗调用 `DELETE /api/chat/memories/{memoryId}`，后端设置 `deleted=1`，后续列表和模型上下文检索都会排除该记录。
6. 管理端治理中心并行加载权限、Hook、项目画像、长期记忆、Slash Command 和审计数据。管理员在「长期记忆」页签对记忆执行“启用/停用”治理操作，页面使用 Ant Design 按钮和消息组件，不使用浏览器原生弹窗。

## 关键文件

- `backend/src/main/java/com/codingx/governance/application/service/ProjectProfileService.java`：扫描工作空间并生成画像 JSON 与 Agent 上下文。
- `backend/src/main/java/com/codingx/governance/application/service/LongTermMemoryService.java`：提取、去重、启用、停用和检索长期记忆。
- `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatMemoryController.java`：用户侧长期记忆查询、正文编辑、启停和逻辑删除接口。
- `backend/src/main/java/com/codingx/governance/application/service/GovernanceAgentContextService.java`：组合项目画像与 ACTIVE 记忆并回注聊天上下文。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatWorkspaceBindingService.java`：目录绑定时刷新画像并返回已生效记忆数量。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：保存画像、记忆列表和状态更新动作。
- `frontend/user/src/views/ChatView.tsx`：渲染“工作区智能”信息条和已生效记忆摘要。
- `frontend/user/src/views/MemoryView.tsx`：用户端长期记忆管理页，承载筛选、编辑、启停、删除和未登录提示。
- `frontend/user/src/App.tsx` 与 `frontend/user/src/components/Sidebar.tsx`：注册 `/memories` 路由并提供侧栏入口。
- `frontend/admin/src/pages/GovernanceCenterPage.tsx`：管理端项目画像增强列和长期记忆页签。

## 关键数据结构

- `governance_project_profile.module_map_json`：模块地图 JSON，记录后端、用户端、管理端等模块。
- `governance_project_profile.test_commands_json`：按模块推断的测试或构建命令。
- `governance_project_profile.key_entrypoints_json`：启动类、前端入口、控制器和配置文件等关键入口。
- `governance_project_profile.risk_points_json`：扫描发现的风险点或验证缺口。
- `governance_project_profile.agent_context`：供模型输入的项目级上下文摘要。
- `governance_long_term_memory`：长期记忆表，当前写入状态为 `ACTIVE`，停用状态为 `REJECTED`，历史 `PENDING` 会通过迁移转为 `ACTIVE`，范围为 `USER` 或 `PROJECT`；用户删除时只更新 `deleted=1`，保留来源审计链路但不再参与列表和上下文回注。

## 测试与验证

- 后端测试覆盖画像字段、迁移/基线结构、记忆提取去重、状态更新、检索、聊天上下文回注和用户/管理端记忆接口。
- 用户端测试覆盖目录绑定响应、长期记忆 API、`useChatWorkspace` 状态同步、“工作区智能”已生效记忆展示、`/memories` 路由接入和记忆管理页的筛选、编辑、启停、删除。
- 管理端测试覆盖治理中心长期记忆页签、项目画像增强字段和启停操作。
- 前端页面改动需要通过浏览器/CDP 打开用户端聊天页和管理端治理中心，保存截图或计算样式证据到 `logs/`。
