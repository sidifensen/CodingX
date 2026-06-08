# User Workspace Inventory Design

## Goal

用户端左侧工作区列表需要展示当前用户已有的全部有效工作空间，而不是只展示本地快照里已有会话的目录。管理端库存页能看到的当前用户工作空间，在用户端也应作为空分组可见，并在没有会话时显示“暂无会话”。

## Scope

本次只新增用户侧只读工作区库存能力，并把库存合并到聊天侧栏工作区分组。管理端 `/api/admin/workspaces` 继续只服务管理员排查页；用户端不能复用管理端接口，避免管理员权限和用户侧展示耦合。

## Backend Design

新增用户侧 `GET /api/chat/workspaces` 接口。控制器只负责协议适配和 `ApiResponse` 封装，当前用户标识由服务层通过登录态读取。服务层从 `workspace` 表读取 `created_by = 当前用户` 且 `deleted = 0` 的记录，按运行目标、名称和更新时间稳定排序，返回工作空间 ID、展示名、运行目标、本地目录、仓库和分支等展示字段。

后端不在列表接口里创建默认空间，不改变工作区、会话或本地目录绑定状态。空工作区也返回，因为用户端需要把它渲染成空分组；其他用户的工作区、逻辑删除工作区不返回。异常继续走全局异常处理器，前端只消费统一 `ApiResponse.message`。

## Frontend Design

`ChatApi` 新增 `listWorkspaces(token)`，集中解析 `/api/chat/workspaces` 响应并把 Long ID 统一转成字符串。`useChatWorkspace` 在用户已登录且启动聊天工作区时加载用户工作区库存，将库存映射成 `WorkspaceConversationGroup`，再与本地快照生成的分组按 `partitionKey` 合并。已有本地快照分组优先保留会话、分页和已读状态；服务端库存只补齐缺失的空分组或工作区 ID。

本地工作区使用 `runtimeTarget=local` 与 `workingDirectory` 生成分区键；云端工作区继续归并到默认云端历史分区，避免出现多个无目录云端空分组。桌面端展示云端和本地所有分组，Web 端仍按当前运行目标过滤。点击空本地工作区分组时沿用现有 `setActiveWorkspacePath`，会先绑定目录，再按返回的 workspaceId 拉取该工作区会话。

## Data Flow

用户打开聊天页后，`useChatWorkspace` 读取登录 token、宿主运行目标和本地快照。初始化能力列表与会话分页的同时，请求 `/api/chat/workspaces` 获取当前用户的有效工作区库存。后端按当前用户归属查询 `workspace` 表，过滤已删除记录后返回统一响应。

前端收到库存后转换为工作区分组候选：本地目录分组带 `workspacePath` 和空会话列表，默认云端分组只补齐标签和运行目标。合并函数以本地快照为权威保留已有会话、置顶、分页和任务提醒状态，再把缺失的库存分组追加进侧栏。最终 `ConversationHistory` 使用现有渲染逻辑展示分组，没有会话时显示“暂无会话”。

## Testing

后端单元测试覆盖仓储按用户列出有效工作区、过滤其他用户和删除工作区、控制器返回字符串 ID 与用户侧字段。前端 hook 测试覆盖服务端返回空本地工作区后，`workspaceGroups` 包含该分组且会话为空。现有侧栏测试已经覆盖空分组显示“暂无会话”，本次不改侧栏视觉结构。
