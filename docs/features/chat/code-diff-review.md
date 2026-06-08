# 聊天代码差异审查

## 功能用途

聊天页和 desktop 端在 AI 调用本地文件工具写代码时，会实时展示正在编辑和已编辑的文件差异。用户可以在助手消息内点击文件，并在该文件行下方展开 diff 面板，也可以通过右侧代码审查栏切换本轮编辑、上轮对话、未暂存、已暂存、提交和分支等差异来源。

## 使用入口

用户在绑定本地工作空间的聊天或 desktop 会话中发送写代码请求后，模型调用 `write`、`edit` 或 `apply_patch` 工具时触发实时差异展示。聊天页右上角提供“代码差异”按钮，默认不展示侧栏；用户点击后才展开右侧代码审查栏，并可拖动侧栏左边缘调整宽度。工作区类差异通过内置 `git_diff` 工具读取当前绑定 workspace 的 git diff。

## 核心流程

1. 模型发起文件工具调用时，`useChatWorkspace` 从 `tool-call start` 事件的 `path`、`content` 或 `patch` 参数构造临时 `fileDiffs`，过程卡片立即带上 `pending` 状态。前端消息区因此能在真实写盘前显示“正在编辑 N 个文件”，并用临时 unified diff 展示 AI 准备新增或替换的内容。
2. 后端 `CodexBuiltinChatToolExecutor` 执行 `write`、`edit`、`apply_patch` 后生成真实文件级 diff，并把 `fileDiffs`、`diffSummary` 和 `diffPreview` 写入工具结果 metadata。`ChatApplicationService` 持久化执行步骤 metadata，历史回放时前端可以从 `executionSteps.metadataJson.resultMetadata` 恢复同一批差异。
3. `ChatView` 收集助手消息过程卡片中的 `fileDiffs`，在消息内渲染编辑文件摘要。用户点击文件行时在该行下方展开 `InlineFileDiffPanel`，面板内使用 `DiffTextBlock` 对 `+`、`-`、hunk 和普通行做分色展示；面板保留在文件列表上下文内，不使用浏览器原生弹窗，也不再额外打开全屏遮罩弹窗。
4. `ChatView` 在右上角渲染代码差异切换按钮，按钮只控制侧栏显隐，不影响消息区的实时文件摘要。`CodeReviewSidebar` 展开后默认展示本轮 AI 编辑差异，用户可点击面板右上角关闭按钮收起；拖动面板左边缘时按右侧固定面板计算宽度，向左拖宽、向右拖窄，并限制在最小和最大宽度之间。
5. 同一工具调用可能连续收到 `start`、`progress` 和 `complete` 事件。`useChatWorkspace` 按卡片 ID 合并过程卡片时，如果后续事件没有携带 `fileDiffs` 或 `diffSummary`，会保留上一帧已经生成的临时或真实差异，避免多轮对话过程中“正在编辑/已编辑文件”列表被空事件覆盖。
6. 同一会话第二轮或后续轮次结束后，前端会重新加载 `messages` 与 `executionSteps`。回放时不只修补最新助手消息，还会按每条助手消息的 `runId` 过滤对应步骤，把早期轮次的 `fileDiffs` 和过程卡片补回原消息，避免第一轮写文件列表在第二轮刷新后消失。
7. 侧栏切到“上轮对话”时只读取上一条带差异的助手消息。切到“未暂存”“已暂存”“提交”或“分支”时，前端通过 `ChatApi.invokeTool(token, 'git_diff', { mode }, { workspaceId, repositoryPath })` 请求当前 workspace 差异；本地运行目标会透传当前绑定目录，云端目标不传本地路径。
8. 用户态 `git_diff` 工具优先按显式 `workspaceId` 查询当前用户拥有的工作空间目录，并只在该目录内绑定工具执行上下文；未命中时才回退到用户最近绑定且与前端 `repositoryPath` 一致的目录。未检测到差异时返回空 `fileDiffs` 和中文提示；检测到差异时解析 unified diff，按文件路径、增删行数和完整 diff 文本返回给前端。

## 关键文件

- `backend/src/main/java/com/codingx/tool/application/service/CodexBuiltinChatToolExecutor.java`：生成文件工具 diff metadata，并提供 `git_diff` 工作区差异查询。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：持久化工具执行步骤 metadata，保证历史回放可以恢复文件差异。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatWorkspaceBindingService.java`：按当前用户和 workspaceId 查询持久化本地工作区目录。
- `backend/src/main/java/com/codingx/tool/application/service/ChatToolUserService.java`：把 `git_diff` 纳入聊天工具调用白名单，并按显式工作区绑定工具执行目录。
- `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatToolController.java`：接收并透传 `workspaceId` 与 `repositoryPath`。
- `frontend/user/src/views/chat/fileDiffs.ts`：统一归一化后端 diff metadata，并从工具开始参数构造临时 pending diff。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：在 SSE start、complete 和历史 replay 链路中写入 `fileDiffs`、`diffSummary`。
- `frontend/user/src/views/ChatView.tsx`：渲染消息内编辑文件摘要、行内 diff 面板和右侧代码审查栏。
- `frontend/user/src/views/chat/chatApi.ts`：提供统一工具调用入口，供右侧栏读取工作区 git diff 并携带当前工作区上下文。

## 关键数据结构

- `FileDiffItem`：包含 `path`、`oldPath`、`newPath`、`status`、`additions`、`deletions` 和 `diff`，用于描述单个文件的差异。
- `DiffSummary`：包含 `filesChanged`、`additions` 和 `deletions`，用于消息摘要、侧栏统计和模式切换后的标题统计。
- 工具 metadata：`fileDiffs` 保存文件级差异列表，`diffSummary` 保存汇总，`diffPreview` 保存拼接后的文本预览，供模型证据、SSE 和历史回放共用。

## 测试与验证

- `npm run test:run -- tests/views/ChatView.test.tsx tests/views/chat/useChatWorkspace.test.ts`
- `npm run test:run -- tests/views/chat/useChatWorkspace.fileDiffRetention.test.ts`
- `npm run test:run -- tests/views/ChatView.test.tsx -t "右侧代码审查栏"`
- `npm run build`
- `mvn -Dtest=ChatToolUserServiceTest,ChatToolControllerTest test`
- `mvn -Dtest=CodexBuiltinChatToolExecutorTest#shortToolNamesShouldOperateInsideBoundWorkspace+applyPatchShouldWriteFileAndReturnDiff+gitDiffShouldReturnUnstagedFileDiffsForReviewSidebar test`
- `mvn -Dtest=ChatApplicationToolCallFlowTest#sendMessageExecutesModelToolCallAndContinuesWithToolEvidence test`
- `mvn -Dtest=ChatToolUserServiceTest test`
- desktop 端绑定 `D:\code\test` 后发送写代码请求，验证工具执行期间出现“正在编辑”，完成后消息区和右侧栏均展示文件差异；点击消息内文件行后 diff 面板应在该文件列表下方展开，且不出现全屏遮罩弹窗，并保存浏览器/CDP 截图证据。
