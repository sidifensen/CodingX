# 本地会话云端持久化

## 功能用途

聊天历史统一由后端数据库承载。Electron 本地运行目标发送消息时，也会创建或复用 `workspace`，并写入 `chat_conversation`、`chat_message`、任务、run 与 trace，方便用户回放和后台审计。

## 使用入口

- 云端模式未选择项目时，发送消息自动归档到“云端历史记录”工作空间。
- Electron 本地模式未选择目录但发起本地请求时，前端允许直接发送，后端归档到“本地历史记录”工作空间。
- Electron 选择本地项目目录后，绑定接口会创建或复用该目录对应的本地工作空间，发送消息时携带该 `workspaceId`。

## 核心流程

1. 前端本地发送前调用本地目录绑定能力，拿到后端返回的 `workspaceId`。
2. `/api/chat/workspace/bind-repository` 校验目录存在且为文件夹，按规范化路径创建或复用 `runtime_target = local` 的 `workspace`。
3. `/api/chat/stream` 收到 `runtimeTarget=local` 后不再设置 `localOnly=true`，而是正常创建会话。
4. 本地请求若没有传 `workspaceId` 但带了 `repositoryPath`，后端会先绑定目录，再用绑定结果创建会话。
5. 聊天执行服务按普通持久化链路写任务、run、MCP/技能/专家绑定、消息和 trace；`repositoryPath` 仍用于本地工具工作目录。
6. 前端流结束后重新按 `workspaceId` 拉取会话列表和消息，localStorage 只作为刷新期间的 UI 快照兜底。

## 关键文件

- `backend/src/main/java/com/codingx/workspace/infrastructure/repository/WorkspaceRepositoryImpl.java`：创建或复用默认本地历史空间和本地目录工作空间。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatWorkspaceBindingService.java`：校验本地目录并返回可归档的 `workspaceId`。
- `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatStreamController.java`：把本地运行目标转为持久化会话请求，并兜底绑定 `repositoryPath`。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatConversationApplicationService.java`：未传工作空间时按运行目标选择默认云端或默认本地历史空间。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：本地发送前同步工作空间绑定，流结束后回查后端历史。

## 关键数据结构

- `workspace.runtime_target`：`cloud` 表示云端历史，`local` 表示本地运行目标。
- `workspace.working_directory`：本地项目目录路径；默认“本地历史记录”空间为空。
- `chat_conversation.workspace_id`：所有新会话都绑定到某个工作空间。
- `SendChatMessageCommand.localOnly`：保留兼容字段，普通 Electron 本地请求不再使用该临时链路。

## 测试与验证

- `mvn "-Dtest=ChatWorkspaceBindingServiceTest,WorkspaceRepositoryImplTest,ChatStreamControllerTest" test`
- `npm run test:run -- tests/views/chat/useChatWorkspace.test.ts tests/views/chat/useChatWorkspace.taskStatus.test.ts`
- `mvn compile`
- `npm run build`
