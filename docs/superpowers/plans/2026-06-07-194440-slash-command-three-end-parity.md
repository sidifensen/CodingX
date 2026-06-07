# 三端 Slash Command 同源实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让管理端维护的 Slash Command 在 Web、桌面端和 CLI TUI 中同源展示，并让 CLI 按 Web 协议提交内置命令。

**Architecture:** 后端 `/api/chat/slash-commands` 继续作为唯一命令目录，Web/桌面端复用现有用户端聊天页；CLI 新增命令目录客户端、TUI 合并渲染和聊天流结构化消息参数。桌面端补充打包态 API 基址，避免内置 `file://` 页面无法访问后端。

**Tech Stack:** Java 21、JUnit 5、Hutool、tui4j、Electron、TypeScript、React/Vite+。

---

### Task 1: CLI Slash Command 目录与 TUI 面板

**Files:**
- Create: `cli/src/main/java/com/codingx/cli/slash/CliSlashCommand.java`
- Create: `cli/src/main/java/com/codingx/cli/slash/SlashCommandCatalog.java`
- Create: `cli/src/main/java/com/codingx/cli/slash/BackendSlashCommandCatalog.java`
- Modify: `cli/src/main/java/com/codingx/cli/tui/CodingXTuiModel.java`
- Modify: `cli/src/main/java/com/codingx/cli/tui/CodingXTuiLauncher.java`
- Modify: `cli/src/main/java/com/codingx/cli/CodingXCli.java`
- Test: `cli/src/test/java/com/codingx/cli/tui/CodingXTuiModelTest.java`
- Test: `cli/src/test/java/com/codingx/cli/slash/BackendSlashCommandCatalogTest.java`

- [x] **Step 1: Write failing TUI tests**

Add tests proving `/` renders `/review` and `/fix-test` from an injected catalog while retaining `/login` and `/logout`.

- [x] **Step 2: Write failing catalog tests**

Use local `HttpServer` to assert `GET /api/chat/slash-commands` carries `satoken`, normalizes numeric ids, and returns empty list for missing token or failed response.

- [x] **Step 3: Implement catalog model and HTTP client**

Implement `CliSlashCommand`, `SlashCommandCatalog`, and `BackendSlashCommandCatalog` with Chinese comments and no hardcoded governance command data.

- [x] **Step 4: Wire TUI rendering**

Inject `SlashCommandCatalog` into launcher/model, cache loaded commands in the model, and render backend commands plus local commands in the slash panel.

### Task 2: CLI 结构化 Slash Command 提交

**Files:**
- Modify: `cli/src/main/java/com/codingx/cli/backend/BackendChatEventSource.java`
- Test: `cli/src/test/java/com/codingx/cli/backend/BackendChatEventSourceTest.java`

- [x] **Step 1: Write failing stream request test**

Assert `/review 请审查当前改动` sends `question=请审查当前改动` and `messages` with `slash_command` + `text` entries.

- [x] **Step 2: Implement request parsing**

Use the same `SlashCommandCatalog` to recognize enabled builtin commands, strip only the leading slash token, and add URL-encoded `messages` JSON. Unknown slash inputs remain normal questions unless the TUI consumed them as local commands.

### Task 3: 桌面端打包态 API 基址

**Files:**
- Modify: `frontend/desktop/src/main.ts`
- Modify: `frontend/desktop/.env.example`
- Modify: `frontend/desktop/.env.production.example`
- Modify: `frontend/desktop/README.md`

- [x] **Step 1: Add production API base configuration**

Add `CODINGX_API_BASE_URL` defaulting to `http://localhost:5001`.

- [x] **Step 2: Rewrite packaged `/api` requests**

In packaged local-file mode, rewrite `file://.../api/...` requests to the configured backend base URL while leaving development and remote `CODINGX_USER_URL` behavior unchanged.

### Task 4: 文档与验证

**Files:**
- Create: `docs/features/agent/slash-command-three-end-parity.md`
- Modify: `docs/features/index.md`

- [x] **Step 1: Update feature documentation**

Document the real entry points, data flow, CLI behavior, desktop packaged behavior, and verification commands.

- [x] **Step 2: Run verification**

Run targeted CLI tests, full CLI `mvn test`, desktop `npm run build`, and user frontend Slash Command tests/build as required by the changed surface.
