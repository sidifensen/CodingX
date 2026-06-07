# 三端 Slash Command 同源

## 功能用途

管理端治理中心维护的启用 Slash Command 作为唯一命令目录，Web 用户端、Electron 桌面端和 CLI TUI 都读取同一份后端用户侧接口。用户在任一端选择或输入 `/review`、`/fix-test` 等内置命令时，最终都按聊天流支持的结构化消息协议提交，避免三端各自硬编码命令。

## 使用入口

- 管理端：治理中心维护 Slash Command 的启用状态、命令编码、展示名、描述、类型和排序。
- Web 用户端：聊天输入框输入 `/` 打开 Slash Command 面板，选择后在输入区显示命令标签，发送时携带结构化 `messages`。
- 桌面端：Electron 继续加载 `frontend/user`，所以命令面板、标签和发送逻辑复用 Web 用户端；打包内置页面通过主进程把 `/api` 请求改写到 `CODINGX_API_BASE_URL`。
- CLI TUI：输入 `/` 展示本地控制命令和后端启用命令；输入 `/review 请审查当前改动` 后，CLI 会识别命中后端内置命令并提交结构化消息。

## 核心流程

1. 管理端保存 Slash Command 后，后端继续通过 `GET /api/chat/slash-commands` 暴露用户可见目录。该接口返回统一 `ApiResponse.data` 数组，包含 `commandCode`、`displayName`、`description`、`commandType` 和 `sortNo` 等字段；调用失败或用户未登录时，下游客户端按空目录降级。
2. Web 用户端进入聊天工作区后，`useChatWorkspace` 通过 `ChatApi.listSlashCommands(token)` 拉取命令目录并写入 `availableSlashCommands`。用户输入 `/` 时，`ChatView` 仅把斜杠后的 token 作为过滤词；选中命令后只保留用户正文，命令本身进入 `selectedSlashCommand` 状态。
3. Web 或桌面端发送消息时，`buildStreamRequestUrl` 会把已选择的内置命令转换为 `messages=[slash_command,text]` 查询参数，并从普通 `question` 中剥离前导 `/command`。如果没有选中命令或命令不是内置命令，仍按普通聊天提交，不额外伪造结构化消息。
4. CLI 启动时由 `CodingXCli` 创建 `BackendSlashCommandCatalog` 并同时注入 `CodingXTuiModel` 和 `BackendChatEventSource`。TUI 渲染 `/` 面板时先加载后端目录并与 `/login`、`/logout`、`/help` 合并展示；目录读取失败不影响本地控制命令和普通聊天。
5. CLI 提交聊天流时，`BackendChatEventSource` 只解析输入开头的 Slash token。命中后端目录中的 `BUILTIN` 命令时，CLI 剥离前导命令，把剩余正文写入 `question`，同时生成与 Web 端一致的 `slash_command` 和 `text` 两段 `messages` JSON；未知 Slash 输入按普通文本处理，仍交给后端聊天流。

## 关键文件

- `backend/src/main/java/com/codingx/governance/interfaces/controller/SlashCommandController.java`：用户侧 Slash Command 目录接口。
- `frontend/user/src/views/chat/chatApi.ts`：Web 用户端命令目录 API 封装。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：加载命令目录并构造聊天流结构化提交参数。
- `frontend/user/src/views/ChatView.tsx`：Slash Command 面板、过滤、选择标签和输入区交互。
- `frontend/desktop/src/main.ts`：桌面主进程加载内置用户端并注册打包态 `/api` 请求改写。
- `frontend/desktop/src/apiProxy.ts`：打包态 `file://.../api/...` 到后端 API 基址的 URL 规整逻辑。
- `cli/src/main/java/com/codingx/cli/slash/BackendSlashCommandCatalog.java`：CLI 后端命令目录客户端。
- `cli/src/main/java/com/codingx/cli/tui/CodingXTuiModel.java`：CLI `/` 面板合并展示后端命令和本地控制命令。
- `cli/src/main/java/com/codingx/cli/backend/BackendChatEventSource.java`：CLI 结构化 Slash Command 聊天流提交。

## 关键数据结构

```json
[
  {
    "type": "slash_command",
    "data": {
      "command": "review",
      "command_type": "builtin"
    }
  },
  {
    "type": "text",
    "data": {
      "content": "请审查当前改动"
    }
  }
]
```

- `CliSlashCommand`：CLI 内部命令快照，字段来自后端目录，`commandCode` 不包含前导斜杠。
- `SlashCommandCatalog`：CLI 命令目录边界，TUI 展示和聊天流提交共用该接口，保证识别规则同源。
- `CODINGX_API_BASE_URL`：桌面打包态后端 API 基址；未配置时默认 `http://localhost:5001`。

## 测试与验证

- `cd cli && mvn "-Dtest=CodingXTuiModelTest,BackendChatEventSourceTest,BackendSlashCommandCatalogTest" test`
- `cd cli && mvn test`
- `cd frontend/desktop && npm run test:run`
- `cd frontend/user && npm run test:run -- tests/views/chat/chatApi.test.ts tests/views/chat/useChatWorkspace.test.ts tests/views/ChatView.test.tsx`
