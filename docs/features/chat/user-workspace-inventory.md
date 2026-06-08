# 用户侧工作区库存展示

## 功能用途

用户端聊天侧栏展示当前登录用户拥有的全部有效工作区。即使某个本地工作区暂时没有会话，本地缓存里也没有快照，只要后端 `workspace` 表仍存在该用户的未删除记录，侧栏也会显示对应分组。

## 使用入口

- 桌面端或用户前端进入聊天页并完成登录态恢复后，`useChatWorkspace` 在首屏初始化阶段读取工作区库存。
- 侧栏工作区树仍优先展示本地快照里的会话、置顶和任务提醒状态；后端库存只负责补齐缺失的空分组。

## 核心流程

1. 前端聊天页启动后，`useChatWorkspace` 从本地登录会话读取 token，并按当前运行目标、工作空间路径、工作空间 ID、URL 会话和桌面宿主上下文生成 bootstrap key。同一 key 正在执行或已经完成时会跳过重复初始化，避免 React StrictMode 或 Electron 鉴权恢复触发重复请求。进入真正初始化后，前端并行发起会话列表、示例题、专家、技能、MCP 和工作区库存请求，保证库存加载不阻塞会话正文恢复。
2. 前端通过 `ChatApi.listWorkspaces` 请求 `GET /api/chat/workspaces`，接口层继续使用统一 `ApiResponse` 解析和鉴权异常类型。接口返回的工作区 ID 统一转字符串，`runtimeTarget` 归一为 `cloud` 或 `local`，空白目录、仓库地址和分支名归一为 `null`。如果库存请求遇到非鉴权异常，聊天首屏继续使用本地快照分组，避免因为补齐空分组失败影响用户进入聊天页。
3. 后端 `ChatWorkspaceInventoryController` 只负责协议适配和 `ApiResponse.success` 封装，不在 Controller 内拼装复杂业务。`ChatWorkspaceInventoryService` 从 Sa-Token 登录态读取当前用户 ID，再调用 `WorkspaceRepositoryImpl.listActiveWorkspacesByUser`。仓储查询固定按 `created_by = 当前用户` 和 `deleted = 0` 过滤，避免用户端复用管理端全量库存造成越权展示。
4. 服务层把 `WorkspaceDO` 投影为 `ChatWorkspaceResponse`，其中 Long 主键转为字符串，运行目标为空时回退为 `cloud`，可选文本统一裁剪空白并把空白值转成 `null`。前端收到本地工作区时，只有 `runtimeTarget = local` 且 `workingDirectory` 有效才生成本地分区；没有目录的本地库存不会展示，防止出现无法进入的空本地分组。云端库存没有目录时会归并到默认云端历史分区。
5. `useChatWorkspace` 合并侧栏分组时先读取 `codingx.chat.workspace.conversations.v1` 生成本地快照分组，再遍历后端库存生成候选分组。本地快照分组已经存在同一 `partitionKey` 时，本地会话列表、置顶 ID、任务完成提醒兼容状态和最近打开时间保持权威，只补齐缺失的展示标签或路径。库存里新增的空工作区会以空 `conversations` 分组追加进侧栏，并沿用本地分区键规范，例如 `local::d:/code/codingx`。
6. 初始化拿到会话列表和库存后，前端重新刷新侧栏全部分组，并继续按原逻辑恢复 URL 会话、本地快照会话或保持首页新建态。鉴权失败仍走既有 `UnauthorizedError` 分支触发登录兜底；库存为空、工作区无会话或本地缓存为空时，用户最终看到的是包含有效空工作区的侧栏，而不是只有“本地历史记录/云端历史记录”的默认分组。

## 关键文件

- `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatWorkspaceInventoryController.java`：用户侧工作区库存接口。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatWorkspaceInventoryService.java`：按登录用户读取工作区并投影安全响应。
- `backend/src/main/java/com/codingx/chat/interfaces/response/ChatWorkspaceResponse.java`：工作区库存响应结构。
- `backend/src/main/java/com/codingx/workspace/infrastructure/repository/WorkspaceRepositoryImpl.java`：新增当前用户有效工作区查询。
- `frontend/user/src/views/chat/chatApi.ts`：新增 `listWorkspaces` 和库存字段归一化。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：合并后端库存与本地快照分组。
- `frontend/user/src/views/chat/types.ts`：新增 `WorkspaceInventoryItem` 类型。

## 关键数据结构

- `ChatWorkspaceResponse.id`：后端工作区 ID 字符串，避免前端 Long 精度丢失。
- `ChatWorkspaceResponse.runtimeTarget`：运行目标，当前按 `cloud` 和 `local` 参与前端分区。
- `ChatWorkspaceResponse.workingDirectory`：本地工作区目录，生成本地分区键的唯一目录来源。
- `WorkspaceInventoryItem`：前端归一化后的库存项，作为补齐空侧栏分组的输入。
- `WorkspaceConversationGroup.partitionKey`：侧栏分组键，仍由 `runtimeTarget + workingDirectory` 生成。

## 验证方式

- 后端仓储测试覆盖当前用户未删除工作区过滤。
- 后端 Controller 测试覆盖 `/api/chat/workspaces` 的统一响应和字符串 ID 契约。
- 前端 Hook 测试覆盖服务端返回空本地工作区时侧栏生成空分组。
- 改动面验证执行后端编译、后端测试、用户前端构建和用户前端测试。
