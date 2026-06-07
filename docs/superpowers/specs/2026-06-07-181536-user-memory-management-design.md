# User Memory Management Design

## Goal

用户端新增“记忆管理”页面，让普通用户能查看和维护自己的长期记忆与当前项目记忆。长期记忆仍按“用户明确要求记住后自动 ACTIVE”的规则生效，但用户必须能在页面里编辑、停用、重新启用或删除不再适用的记忆。

## Scope

- 用户端侧边栏新增“记忆管理”入口，路由为 `/memories`。
- 页面默认读取当前用户可见记忆；未绑定项目时只展示用户级记忆，绑定项目后同时展示用户级记忆和当前项目记忆。
- 页面支持按范围筛选：全部、用户记忆、项目记忆；支持按状态筛选：全部、已生效、已停用。
- 页面支持编辑记忆正文、启用/停用记忆、删除记忆。
- 后端用户接口补齐编辑和逻辑删除能力，并复用现有归属校验，禁止用户操作他人记忆。
- 普通聊天内容不会自动沉淀为长期记忆；只有明确“记住/长期保存/以后都按”等授权表达才会生成 ACTIVE 记忆。

## Architecture

后端在 `ChatMemoryController` 暴露用户侧记忆管理接口，控制器只做协议适配和当前登录用户读取，归属校验、内容规范化、关键词更新、逻辑删除都下沉到 `LongTermMemoryService`。仓储继续通过 `governance_long_term_memory.deleted` 过滤列表和详情，不新增表结构。

前端新增 `MemoryView` 作为路由级页面，直接复用 `ChatApi` 和 `AuthStorage` 获取会话令牌。页面局部维护筛选、加载、错误、编辑弹窗和删除弹窗状态；应用壳层只负责路由和传入当前工作空间信息，不在聊天页里塞管理逻辑。

## Data Flow

1. 用户进入 `/memories` 或点击侧边栏“记忆管理”后，`MemoryView` 读取登录 token，并调用 `ChatApi.listLongTermMemories(token, workspaceId, status)`。
2. 后端 `ChatMemoryController.listMemories` 从 Sa-Token 获取当前用户 ID，将 `workspaceId/status` 传给 `LongTermMemoryService.listUserMemories`。
3. `LongTermMemoryService` 要求用户 ID 存在，并调用仓储按用户、当前工作空间和状态过滤；`workspaceId` 为空时不会混入其他项目记忆。
4. 用户编辑正文时，页面调用 `PATCH /api/chat/memories/{id}`；服务校验记忆归属，正文不能为空，更新 `content`、`keywordJson`、`memoryKey` 和 `updatedAt`。
5. 用户停用或启用时，页面调用已有状态接口；ACTIVE 参与模型回注，REJECTED 不参与回注。
6. 用户删除时，页面调用 `DELETE /api/chat/memories/{id}`；服务逻辑删除后该记忆不再出现在列表，也不会参与模型回注。

## UI Design

页面采用工作台式密集布局：顶部是标题、当前工作空间和刷新按钮；中部是分段筛选条；主体是列表式记忆条目，不使用卡片套卡片。每条记忆展示范围、状态、正文、来源、更新时间和操作按钮。编辑与删除使用自定义弹窗，禁止浏览器原生弹窗。

亮暗主题沿用 `bg-background`、`bg-surface`、`bg-surface-container`、`text-foreground`、`text-muted` 和 `border-border` 等统一令牌。滚动容器使用现有全局滚动条主题，保证暗色模式下不出现亮色系统滚动条。

## Error Handling

前端接口错误直接展示 `ApiResponse.message` 解析后的错误文案，不重写后端语义。未登录时页面展示登录提示并提供登录按钮，不发起记忆请求。编辑空内容时前端先阻止提交，后端仍会兜底返回中文业务异常。

## Testing

- 后端控制器测试覆盖编辑和删除接口会传入当前登录用户。
- 后端服务测试覆盖归属校验、空内容校验、编辑后关键词更新、逻辑删除后列表和回注排除。
- 前端 API 测试覆盖编辑和删除请求。
- 前端页面测试覆盖列表筛选、编辑、启停、删除、未登录提示。
- App/Sidebar 测试覆盖 `/memories` 路由和侧栏入口。
