# 仓库规范文件与长期记忆

## 功能用途

仓库规范文件与长期记忆用于让 Agent 在执行项目任务前获得稳定上下文。仓库规范文件来自当前绑定工作目录内的 AGENTS、CLAUDE、GEMINI 以及常见 AI 编程工具规则文件，后端只读加载后作为运行时上下文注入；长期记忆负责从明确“记住/长期保存/以后都按”等用户授权表达中提取记忆，保存为 `ACTIVE` 后参与后续聊天上下文回注。原本的仓库扫描画像表、管理端扫描接口和画像页签已下线，当前链路不再保存技术栈、模块地图或测试命令摘要。

## 使用入口

- 用户端聊天页：本地工作空间绑定后，聊天提交链路会按当前 `workspaceId` 加载仓库规范文件与已生效长期记忆，前端不再展示扫描画像摘要。
- 用户端记忆管理页：侧栏点击「记忆管理」或直接访问 `/memories`，查看当前账号所有工作空间的用户记忆和项目记忆，并执行筛选、编辑、启用、停用和删除。
- 用户端记忆接口：`GET /api/chat/memories` 查询当前用户可见记忆，管理页通过 `includeAllWorkspaces=true` 查询当前账号全部工作空间记忆，响应会按当前用户从 `workspace` 表补齐项目记忆的 `workspaceName`；`PATCH /api/chat/memories/{memoryId}` 更新记忆正文，`PATCH /api/chat/memories/{memoryId}/status` 启用或停用已有记忆，`DELETE /api/chat/memories/{memoryId}` 逻辑删除记忆。
- 管理端治理中心：进入「治理中心」，在「长期记忆」页签治理已提取记忆；仓库规范文件为聊天运行时只读上下文，不提供管理端编辑或扫描入口。
- 管理端治理接口：`GET /api/admin/governance/long-term-memories` 和 `PATCH /api/admin/governance/long-term-memories/{id}/status` 管理长期记忆启停状态。

## 核心流程

1. 用户端选择或切换本地仓库目录后，`useHostContext` 调用 `ChatApi.bindWorkspaceRepository`，后端 `ChatWorkspaceBindingService` 校验目录、创建或复用本地工作空间，并保存 `workspace.working_directory`。该绑定流程不再触发扫描画像生成，也不会写入画像类持久化数据；绑定响应继续提供 `workspaceId` 和已生效记忆数量，前端同步到 `useChatWorkspace` 状态。目录缺失、越权或不合法时由后端抛出中文业务异常，前端展示 `ApiResponse.message`。
2. 聊天提交时，`ChatApplicationService` 在构造模型历史前调用 `GovernanceAgentContextService`。该服务把当前用户 ID、`workspaceId` 和本轮问题传给仓库规范文件服务与长期记忆服务；任一来源为空时跳过该片段，不影响聊天主流程。最终生成的治理上下文作为 system prompt 片段插入模型历史，与系统提示、Plan mode、搜索证据、专家和技能上下文并列。
3. `RepositoryInstructionContextService` 先按当前用户和 `workspaceId` 查询 `workspace` 表，确认本地工作目录存在且归属当前用户。随后按固定优先级读取 `AGENTS.override.md`、`AGENTS.md`、`CLAUDE.local.md`、`CLAUDE.md`、`.claude/CLAUDE.md`、`GEMINI.md`、`QWEN.md`，并扫描 Cursor、Windsurf、Cline、Roo、Continue、Junie、OpenHands、Kiro、Aider 等常见规则文件或规则目录。它明确排除 `.github/copilot-instructions.md`、`docs/superpowers/memory/**`、`.codingx/context.md` 和 `.codingx/rules.md`，避免把 Copilot 专用指令、系统记忆或本产品内部上下文重复注入。
4. 规范文件读取全程只读，不格式化、不生成、不回写仓库文件；同一路径只保留第一次命中，目录型规则按相对路径稳定排序。候选文件如果是符号链接会被跳过，避免仓库内规则文件指向工作目录外的本地文件并进入模型上下文或日志预览。单文件内容超过 `12,000` 字符时裁剪并追加“内容已截断”提示，本轮累计超过 `24,000` 字符时停止追加后续文件；目录扫描失败、文件读取失败、单文件截断或总量截断都会写入日志，但不会中断聊天。
5. 长期记忆检索按当前用户、工作空间和本轮问题读取最多 6 条 ACTIVE 记录，生成“长期记忆”上下文段。`LongTermMemoryService` 先按用户和当前 `workspaceId` 取候选，再按正文包含、关键词命中和项目主题短问句命中筛选；例如当前工作空间已有“这个项目是斯蒂芬森的”时，用户问“这个项目是谁的”会按 `PROJECT_TOPIC_QUESTION` 命中并回注。回注日志拆成“长期记忆回注开始”和“长期记忆回注结果”，开始日志保留 userId、workspaceId、limit 和问题预览，结果日志只输出候选数、命中数、命中 ID 与命中/过滤原因，避免中间阶段反复打印完整上下文字段。助手正常完成后，`GovernanceAgentContextService.extractMemoryCandidates` 委托 `LongTermMemoryService` 读取用户消息；只有文本包含“记住”“请记忆”“长期保存”“以后都按”“我的偏好”等显式授权信号时才生成 `ACTIVE` 记忆，并按范围、用户、工作空间和内容生成确定性去重键。重复内容不会再次保存，普通聊天不会被自动沉淀为长期记忆。
6. 用户进入 `/memories` 后，`MemoryView` 读取登录令牌并调用 `ChatApi.listLongTermMemories(token, null, 'ALL', { includeAllWorkspaces: true })`，一次性加载当前用户所有工作空间的 ACTIVE 与 REJECTED 记忆。后端 `ChatMemoryViewService` 会按当前用户读取 `workspace.name` 并返回 `workspaceName`，页面优先使用该可信名称，再用侧栏 `workspaceGroups` 作为历史缓存兜底；编辑、启停和删除都使用项目内自定义弹窗或按钮，并沿用后端中文错误消息。
7. 管理端治理中心并行加载权限、自动化 Hook 规则、长期记忆、Slash Command 和权限审计数据。管理员在「长期记忆」页签对记忆执行“启用/停用”治理操作，页面使用 Ant Design 按钮和消息组件，不使用浏览器原生弹窗。由于仓库规范文件只在聊天运行时从本地工作目录读取，管理端不会展示或缓存这些文件内容。

## 关键文件

- `backend/src/main/java/com/codingx/governance/application/service/RepositoryInstructionContextService.java`：解析 workspace 工作目录，发现、排除、读取、截断和渲染仓库规范文件。
- `backend/src/main/java/com/codingx/governance/application/service/GovernanceAgentContextService.java`：组合仓库规范文件与 ACTIVE 记忆并回注聊天上下文。
- `backend/src/main/java/com/codingx/governance/application/service/LongTermMemoryService.java`：提取、去重、启用、停用和检索长期记忆，并区分模型上下文的工作空间查询与管理页全工作空间查询。
- `backend/src/main/java/com/codingx/chat/interfaces/controller/ChatMemoryController.java`：用户侧长期记忆查询、正文编辑、启停和逻辑删除接口。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatMemoryViewService.java`：将长期记忆投影为用户端响应，并按当前用户补齐项目记忆所属工作空间名称。
- `backend/src/main/java/com/codingx/chat/interfaces/response/ChatLongTermMemoryResponse.java`：用户端长期记忆响应结构，包含 `workspaceName` 展示字段。
- `frontend/user/src/views/MemoryView.tsx`：用户端长期记忆管理页，承载全工作空间分组、筛选、编辑、启停、删除和未登录提示。
- `frontend/admin/src/pages/GovernanceCenterPage.tsx`：管理端长期记忆治理入口。

## 关键数据结构

- 仓库规范文件：运行时只读上下文来源，不落库；支持根目录精确文件、指定工具规则目录和 Roo 变体规则目录。
- 规范文件排除列表：`.github/copilot-instructions.md`、`docs/superpowers/memory/**`、`.codingx/context.md`、`.codingx/rules.md`。
- 规范文件截断限制：单文件最多 `12,000` 字符，本轮总计最多 `24,000` 字符，日志预览最多 `48` 字符。
- `governance_long_term_memory`：长期记忆表，当前写入状态为 `ACTIVE`，停用状态为 `REJECTED`，范围为 `USER` 或 `PROJECT`；用户删除时只更新 `deleted=1`，保留来源审计链路但不再参与列表和上下文回注。
- 长期记忆回注匹配原因：`CONTENT_OVERLAP` 表示正文与问题互相包含，`KEYWORD` 表示问题命中记忆关键词，`PROJECT_TOPIC_QUESTION` 表示项目类短问句命中当前工作空间项目记忆，`QUERY_NOT_RELATED` 表示候选存在但本轮问题不相关。

## 测试与验证

- 后端测试覆盖仓库规范文件命中、排除路径、符号链接跳过、短预览日志、超长内容截断、长期记忆提取去重、状态更新、检索、聊天上下文回注和用户/管理端记忆接口。
- 用户端测试覆盖长期记忆 API、`useChatWorkspace` 状态同步、`/memories` 路由接入和记忆管理页的筛选、编辑、启停、删除。
- 管理端测试覆盖治理中心长期记忆页签和启停操作。
- 前端页面改动需要通过浏览器/CDP 打开用户端聊天页和管理端治理中心，保存截图或计算样式证据到 `logs/`。
