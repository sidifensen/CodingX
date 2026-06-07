# 三端 Slash Command 同源设计

## 背景

管理端治理中心已经通过 `governance_slash_command` 维护真实 Slash Command，用户 Web 端已经通过 `/api/chat/slash-commands` 加载启用命令，并在提交时生成后端可解析的 `messages` 结构化参数。桌面端 Electron 当前复用 `frontend/user` 页面，CLI TUI 当前只展示本地 `/login`、`/logout`、`/help`，没有接入治理中心命令。

## 目标

- 管理端展示和维护的启用 Slash Command 必须成为 Web、桌面端和 CLI 的唯一共享命令目录。
- 桌面端继续复用用户端聊天页，不新增第二套聊天 UI；打包态内置页面也必须能访问后端 `/api`。
- CLI TUI 输入 `/` 时展示本地控制命令和后端启用命令；输入后端内置命令时按 Web 端协议提交结构化消息。

## 设计

后端不新增表和 Controller，继续使用 `GET /api/chat/slash-commands` 作为用户侧命令目录。Web 端已有 `ChatApi.listSlashCommands`、`ChatView` 面板和 `useChatWorkspace.buildStreamRequestUrl` 结构化提交逻辑，桌面端通过 Electron 加载 `frontend/user` 直接继承这套实现。

CLI 新增轻量命令目录边界：`SlashCommandCatalog` 定义 TUI 需要的命令读取能力，生产实现通过 `CliConfigStore` 读取 `serverUrl` 与 `token` 后请求 `/api/chat/slash-commands`，解析 `ApiResponse.data` 并规整 `commandCode`、`displayName`、`description`、`commandType`、`sortNo`。TUI 将本地控制命令与后端命令合并渲染，本地命令仍由 CLI 消费，后端命令不再进入未知命令分支。

CLI 聊天流请求在 `BackendChatEventSource` 内识别首个 Slash Command token。命中后端目录中的内置命令时，`question` 去掉前导命令，额外携带 `messages=[slash_command,text]`，字段名和结构与 Web 端保持一致。目录加载失败、未登录或后端不可达时，TUI 仍显示本地控制命令；普通聊天流保持原有登录兜底。

桌面端补充 `CODINGX_API_BASE_URL` 配置。打包态加载内置 `user-dist/index.html` 时，主进程注册请求拦截，把 `file://.../api/...` 或内置页面相对 `/api` 请求改写到后端基址；开发态仍走 Vite 代理或显式 `CODINGX_USER_URL`。

## 测试

- CLI 模型测试：输入 `/` 展示后端 Slash Command 和本地命令；关键字过滤能匹配 `/fix-test`。
- CLI 后端请求测试：`/review 请审查当前改动` 去掉命令前缀并携带结构化 `messages`。
- CLI 目录客户端测试：带 `satoken` 请求 `/api/chat/slash-commands`，解析启用命令；失败时返回空列表。
- 桌面端构建验证：`npm run build` 通过，类型覆盖新增 API 基址与拦截逻辑。
- 用户端既有 Slash Command 测试继续通过，证明桌面复用路径没有破坏 Web 行为。
