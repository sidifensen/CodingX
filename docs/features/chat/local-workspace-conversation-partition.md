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
6. 聊天工作区首屏初始化会按登录 token、当前分区、运行目标、工作空间路径、工作空间 ID、URL 会话和桌面宿主上下文生成 bootstrap key；同 key 已在执行或已完成时跳过后续初始化，避免 Electron 开发态鉴权恢复和 React StrictMode 重复触发同一组会话列表、示例题、专家、技能与 MCP 请求。
7. 读取 `codingx.chat.workspace.conversations.v1` 时会记录 localStorage 原始字符串和已归一化的结构化 store；同一份原始内容在同一渲染进程内再次读取时直接复用解析结果，避免桌面端侧栏刷新、会话恢复和归属查询在同一轮渲染中重复 `JSON.parse` 大快照。
8. 所有快照写入都会先执行分区归一化，再同步写入 localStorage 并刷新解析缓存；如果读取旧缓存时发现需要迁移，也会立即写回归一化结果，保证缓存命中不绕过旧数据合并规则。

## 关键文件

- `frontend/user/src/views/chat/localConversationStorage.ts`：负责工作空间分区键生成、快照读写、旧缓存迁移和重复分区合并。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：负责按当前分区恢复聊天主区，并对同上下文 bootstrap 做去重，确保分区切换仍能正常重新初始化。
- `frontend/user/tests/views/chat/localConversationStorage.test.ts`：覆盖默认分区标签、本地路径标签、正反斜杠归一、旧缓存迁移和本地缓存未变化时的解析复用。
- `frontend/user/tests/views/chat/useChatWorkspace.test.ts`：覆盖鉴权状态切换后 URL 会话恢复不被重复 bootstrap 覆盖，且同上下文只请求一次会话列表。

## 关键数据结构

- `codingx.chat.workspace.conversations.v1`：浏览器本地缓存中的工作空间会话仓库。
- `workspaceStoreCache`：渲染进程内最近一次已解析的快照仓库缓存，按 localStorage 原始字符串判断是否可复用。
- `snapshots[partitionKey]`：按 `cloud::__no_workspace__`、`local::d:/code/codingx` 等分区键保存的快照。
- `workspacePath`：当前快照对应的原始工作空间路径，可为空。
- `workspaceLabel`：侧栏展示名称，由分区语义或目录名推导，不能使用内部空模板占位名。

## 边界约束

- 路径归一化只用于缓存分区键，不改变用户原始工作空间路径的业务含义。
- 历史分区 `__history__` 只用于旧数据迁移，读取后并回默认分区，左侧不单独渲染历史键。
- 合并重复分区时按会话 ID 去重，优先保留先读到的会话记录，避免覆盖已有回放内容。
- 解析缓存只在 localStorage 原始字符串完全一致时命中；外部写入、清空或旧数据迁移后会自动失效或刷新。
- bootstrap 去重只拦截同一个 key；用户切换运行目标、工作空间目录、URL 会话或桌面宿主绑定目录后会生成新 key，并允许重新加载对应分区。

## 测试与验证

- `npm run test:run -- tests/views/chat/localConversationStorage.test.ts`
- `npm run test:run -- tests/views/chat/useChatWorkspace.test.ts`
- `npm run test:run -- tests/components/Sidebar.test.tsx`
- `npm run build`
- `npm run test:run`
