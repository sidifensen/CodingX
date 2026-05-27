# 本地工作空间会话分区

## 功能用途

聊天侧栏按运行环境和工作空间路径隔离会话，确保本地模式下不同目录的历史记录互不混淆，同一目录不会因路径写法差异重复显示。

## 使用入口

用户在本地模式打开工作空间、发送消息或刷新页面时，前端会从本地缓存读取并渲染工作空间分组。

## 核心流程

1. 写入会话快照前，按 `runtimeTarget + workspacePath` 生成分区键。
2. 分区键中的路径统一 trim、反斜杠转正斜杠、去掉尾部斜杠并转小写。
3. 读取缓存时会把旧版正反斜杠重复分区迁移到同一个规范分区。
4. 重复分区合并时保留会话列表、会话记录、置顶顺序和任务完成提醒已读兜底状态。
5. 有路径的工作空间默认用目录名展示，默认分区固定展示为“本地历史记录”或“云端历史记录”。

## 关键文件

- `frontend/user/src/views/chat/localConversationStorage.ts`：负责工作空间分区键生成、快照读写、旧缓存迁移和重复分区合并。
- `frontend/user/tests/views/chat/localConversationStorage.test.ts`：覆盖默认分区标签、本地路径标签、正反斜杠归一和旧缓存迁移。

## 关键数据结构

- `codingx.chat.workspace.conversations.v1`：浏览器本地缓存中的工作空间会话仓库。
- `snapshots[partitionKey]`：按 `cloud::__no_workspace__`、`local::d:/code/codingx` 等分区键保存的快照。
- `workspacePath`：当前快照对应的原始工作空间路径，可为空。
- `workspaceLabel`：侧栏展示名称，由分区语义或目录名推导，不能使用内部空模板占位名。

## 边界约束

- 路径归一化只用于缓存分区键，不改变用户原始工作空间路径的业务含义。
- 历史分区 `__history__` 只用于旧数据迁移，读取后并回默认分区，左侧不单独渲染历史键。
- 合并重复分区时按会话 ID 去重，优先保留先读到的会话记录，避免覆盖已有回放内容。

## 测试与验证

- `npm run test:run -- tests/views/chat/localConversationStorage.test.ts`
- `npm run test:run -- tests/views/chat/useChatWorkspace.test.ts`
- `npm run test:run -- tests/components/Sidebar.test.tsx`
- `npm run build`
- `npm run test:run`
