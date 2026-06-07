# CLI Browser Auth Design

**Date:** 2026-06-07
**Status:** Approved

## Goal

让 `codingx` TUI 在未登录或 token 失效时，引导用户通过浏览器完成登录，并把有效 `satoken` 安全写入用户主目录 `~/.codingx/cli.yml`。主路径使用本机 loopback 回调，备用路径使用设备码登录，开发调试入口保留手动 token 写入。

## Current Context

- CLI 当前通过 `CliConfigStore` 读取 `serverUrl`、`token`、`approvalPolicy` 和 `lastSessionId`，聊天流请求由 `BackendChatEventSource` 调用后端 `/api/chat/stream` 并携带 `satoken`。
- 后端认证已有 `/api/auth/login`、`/api/auth/logout`、`/api/auth/me`，登录结果返回 `LoginResponse` 和 Sa-Token 访问令牌。
- 用户端前端已有 `useAuth`、`AuthApi`、`AuthStorage` 和登录弹窗，但没有 CLI 专用登录页。
- 当前失效 token 请求 SSE 时，后端返回 401 但因为 `Accept: text/event-stream` 和全局 JSON 异常响应协商冲突，CLI 只能看到泛化错误。

## Recommended Architecture

CLI 登录拆成三条路径：

1. **Loopback 浏览器登录主路径。** CLI 启动本机 `127.0.0.1:<randomPort>/callback` 临时回调服务，生成 `state`、`codeVerifier`、`codeChallenge`，打开用户端 `/cli-login` 页面。浏览器登录后调用后端创建一次性授权码，再跳回 CLI 本机回调地址。CLI 校验 `state`，用 `code + codeVerifier` 调后端换取 `satoken` 并保存配置。
2. **Device code 备用路径。** CLI 无法打开浏览器、运行在远程 SSH 或用户选择设备码时，向后端创建设备授权会话，终端显示验证码和网页登录地址。用户在 `/cli-login?deviceCode=...` 登录授权，CLI 轮询后端直到拿到 `satoken` 或超时。
3. **手动 token 调试路径。** 保留现有 `codingx login <serverUrl> <satoken>` 作为开发和排障入口，不作为普通用户首选体验。

## Backend Design

新增认证应用服务 `CliAuthApplicationService`，只负责 CLI 授权码、设备码和 token 兑换流程，不改变聊天 Controller。

后端新增接口挂在 `/api/auth/cli`：

- `POST /api/auth/cli/authorize`
  - 浏览器已登录后调用，必须携带有效 `satoken`。
  - 输入 `state`、`redirectUri`、`codeChallenge`。
  - 只允许 `redirectUri` 使用 `http://127.0.0.1:<port>/callback`、`http://localhost:<port>/callback` 或 `http://[::1]:<port>/callback`。
  - 生成 2 分钟有效、一次性使用的 `authorizationCode`，绑定 `userId`、`state`、`redirectUri` 和 `codeChallenge`。

- `POST /api/auth/cli/token`
  - CLI 调用，输入 `authorizationCode`、`state`、`codeVerifier`。
  - 服务端用 `codeVerifier` 计算 challenge，与创建授权码时保存的 `codeChallenge` 比对。
  - 成功后让同一用户重新创建 Sa-Token 会话，返回现有 `LoginResponse` 结构。
  - 授权码兑换后立即失效；过期、重复使用、state 不匹配或 verifier 不匹配时返回中文错误。

- `POST /api/auth/cli/device/start`
  - CLI 调用，创建 10 分钟有效设备授权会话。
  - 返回 `deviceCode`、`userCode`、`verificationUri`、`expiresInSeconds`、`pollIntervalSeconds`。

- `POST /api/auth/cli/device/authorize`
  - 浏览器已登录后调用，输入 `userCode`。
  - 绑定当前登录用户到设备授权会话，状态从 `PENDING` 变为 `APPROVED`。

- `POST /api/auth/cli/device/token`
  - CLI 轮询调用，输入 `deviceCode`。
  - `PENDING` 返回 202 或统一响应中的 `authorization_pending` 语义；`APPROVED` 返回 `LoginResponse` 并消费设备会话；过期或无效返回中文错误。

短期状态存储优先使用内存 TTL 容器，避免本次新增数据库表。后续若部署为多实例，再把 `CliAuthStateStore` 的实现替换为 Redis。服务层通过接口隔离存储实现，保证替换时 Controller 和 CLI 协议不变。

## Frontend Design

新增用户端页面 `/cli-login`，不复用弹窗，而是独立全屏授权页：

- 页面读取 `redirectUri`、`state`、`codeChallenge`、`deviceCode` 查询参数。
- 未登录时显示账号密码登录表单；开发模式可沿用 `admin / 123456` 默认值。
- 已登录时展示当前账号和授权目标，用户点击授权后调用后端 CLI 授权接口。
- loopback 模式成功后跳转到 `redirectUri?code=...&state=...`。
- device 模式成功后展示“授权完成，请回到终端”。
- 页面支持暗色和亮色主题，复用现有 CSS 主题变量，避免卡片套卡片。
- 所有接口错误优先展示后端 `ApiResponse.message`。

## CLI Design

新增 CLI 登录服务与本机回调服务：

- `CliAuthService`
  - 读取配置中的 `serverUrl`。
  - 支持 `loginWithBrowser()`：启动 `LoopbackCallbackServer`、生成 state/verifier/challenge、打开浏览器、等待回调、调用 token 兑换接口、保存 `CliConfig`。
  - 支持 `loginWithDeviceCode()`：创建设备会话、显示验证码、按后端 interval 轮询 token。
  - 支持 `validateToken()`：启动或发送前用 `/api/auth/me` 校验本地 token，失败时进入登录提示。

- `LoopbackCallbackServer`
  - 只监听 `127.0.0.1`。
  - 只处理 `/callback`。
  - 校验回调 `state` 后把 `code` 交给 CLI。
  - 回调响应给浏览器一个简洁成功页，提示可回到终端。
  - 超时或完成后关闭服务。

- `CliCommandRunner`
  - `codingx` 和 `codingx tui` 仍启动 TUI。
  - `codingx auth login` 或 TUI 登录提示可触发浏览器登录。
  - `codingx auth login --device` 触发设备码登录。
  - `codingx login <serverUrl> <satoken>` 保留为手动调试入口。

- `BackendChatEventSource`
  - HTTP `Accept` 改为同时接受 `text/event-stream, application/json`，401 时能读取后端 `ApiResponse.message`。
  - 若返回未登录语义，TUI 展示“未登录或登录已失效，请重新登录”，并给出登录快捷入口。

## Error Handling

- redirectUri 非本机地址：后端返回 400 中文提示。
- 授权码过期、重复兑换、state/verifier 不匹配：后端返回 400 中文提示，CLI 不保存 token。
- 设备码 pending：CLI 保持等待并渲染剩余状态，不当成错误。
- 设备码过期或取消：CLI 停止轮询并提示重新登录。
- 浏览器打不开：CLI 自动提示设备码登录命令和 verification URI。
- 后端不可达：CLI 保留 TUI 输入区，显示具体连接错误和当前 `serverUrl`。

## Testing Strategy

- 后端单测覆盖授权码创建、redirectUri 校验、PKCE 校验、一次性消费、设备码 pending/approved/expired。
- CLI 单测用 JDK `HttpServer` 模拟后端，覆盖 loopback 回调、token 写入、设备码轮询、SSE 401 错误消息解析。
- 前端单测覆盖 `/cli-login` 未登录表单、已登录授权、loopback 跳转、device 完成提示和错误展示。
- 多模块验证执行 `mvn compile`、`mvn test`、`cli` 模块 `mvn test/package`、用户前端 `npm run build`、`npm run test:run`。
- 前端新增页面必须通过 CDP 打开 `/cli-login` 做至少一次视觉验证，保存截图到 `logs/`。

