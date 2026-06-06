# 聊天技能上下文持久化

## 功能用途

用户在聊天输入区选择技能后，技能选择同时具备两层语义：一是作为可追溯的消息内容前缀写入 `chat_message.content`，二是把对应 `SKILL.md` 注入本轮模型系统上下文，避免短问题被普通闲聊或关于助手意图吞掉。

## 使用入口

聊天页选择技能或输入 `@skill` 后发送消息。前端仍通过 `skillCodes` 参数声明当前选择；后端保存用户消息时统一写成 `@web-access 这是啥` 这类可读内容，消息回放接口再从内容前缀解析 `skillCodes` 给前端展示技能 chip。

## 核心流程

1. `ChatStreamController` 合并显式 `skillCodes` 与结构化技能命令，生成 `SendChatMessageCommand`。
2. `ChatApplicationService` 合并正文前缀与请求参数里的技能码，用户消息落库前用 `ChatCapabilityMentionSupport` 加上 `@skill` 前缀。
3. 改写、意图识别、标题、摘要和模型历史使用剥离前缀后的纯正文，避免把 `@skill` 当自然语言。
4. `ChatSkillContextService` 读取已选技能根级 `SKILL.md` 并追加到系统提示，日志会输出技能上下文是否生效；同时调用 `SkillRuntimeService` 解析 `.codex-skill/skill.json`、`resources/` 与 `scripts/`，把工具声明、资源文件和脚本文件追加为“技能运行时元数据”。当用户正文使用“这个”“这些”“有什么区别”等指代时，提示词会先把指代对象绑定到本轮已选技能；当用户只问“这是什么”“这是啥”等短句时，模型应基于技能说明解释当前引用技能的用途，而不是被系统自我介绍意图吞掉。对象存储版 `web-access` 会额外追加 CodingX 运行时约束，明确当前本地工具命令在 Windows PowerShell 中执行，并要求 CDP Proxy 先通过 `/targets` 复用用户现有 Chrome tab，找不到匹配目标时才 `/new` 创建后台页。
5. 聊天主流程继续按改写结果进入意图识别、模型调用和工具白名单校验；即便用户只问“这是啥”，也会保存真实助手消息与 `chat_execution_run.intent_code`，不会再落库 `skill.intro` 这类伪完成状态。
6. 模型工具调用只允许执行本轮真实暴露的工具 schema；若模型把 `web-access` 这类 skill code 伪造成 tool_call，后端仅忽略这个错误的 tool_call，并回灌“已选技能仍然有效、技能编码不是工具名”的约束。
7. `SkillLocalCacheService` 为云端临时下载的 `web-access` 写入 `WEB_ACCESS_BROWSER=chrome`，避免每轮新目录都要求用户重新选择浏览器。
8. 本轮技能、MCP、专家上下文写入隐藏的 `chat_execution_step(step_type=runtime_context)`，旧的 `task_skill`、`task_mcp`、`task_event`、`task_artifact`、`task_expert` 和 `task` 表不再使用。
9. 前端用户消息展示时只在 chip 里显示技能码，正文剥离开头 `@skill`，复制、编辑和分享预览也使用可见正文。

## 关键文件

- `backend/src/main/java/com/codingx/chat/application/service/ChatCapabilityMentionSupport.java`：格式化、解析和剥离消息正文中的技能前缀。
- `backend/src/main/java/com/codingx/chat/application/service/ChatRunContextStepSupport.java`：把运行能力上下文编码到 `chat_execution_step`。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：落库用户消息、注入技能上下文、隐藏运行上下文步骤。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatWorkspaceQueryService.java`：从 `runtime_context` 读取当前技能与 MCP，并对普通步骤列表隐藏该内部步骤。
- `backend/src/main/java/com/codingx/skill/application/service/SkillLocalCacheService.java`：下载 skill 临时目录，并为 `web-access` 补齐默认浏览器配置。
- `backend/src/main/java/com/codingx/skill/application/service/SkillRuntimeService.java`：解析技能包元数据、资源文件和脚本文件。
- `backend/src/main/java/com/codingx/skill/application/service/SkillResourceBoundaryService.java`：读取技能资源前校验相对路径，拒绝越界访问。
- `frontend/user/src/views/ChatView.tsx`：展示层剥离用户消息开头技能标记，避免和技能 chip 重复。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：发送前解析 `@skill` 前缀，支持与后端一致的技能编码字符集。
- `backend/src/main/resources/db/migration/V20260529_200500__migrate_task_context_to_chat_execution_step.sql`：迁移旧任务技能与 MCP 能力上下文并删除旧绑定表。
- `backend/src/main/resources/db/migration/V20260605_160009__remove_task_tables_from_chat_runtime.sql`：把旧 `task_expert` 专家绑定补写到运行上下文后删除 `task_expert` 与 `task`。
- `backend/src/main/resources/db/schema.sql`：基线结构不再包含旧任务表、任务专家绑定和任务产物/事件表。

## 关键逻辑

`chat_message.content` 是用户可审计输入，不再把技能选择隐藏在独立绑定表里。模型侧不能直接使用这个持久化内容，因为 `@web-access 这是啥` 会干扰改写、意图和回答，因此所有入模历史都会经过 `ChatCapabilityMentionSupport.toPlainAiMessage` 剥离前缀。

由于入模用户正文会剥离开头 `@skill`，技能系统提示必须补足指代关系：带有明确目标的 `@web-access 帮我看 https://example.com` 会保留技能上下文进入模型；`@web-access 这是啥` 这类短句会先解释当前引用的技能本身，不能伪造成已经执行联网操作。多个技能同时选中且用户提供了可对比对象时，“这个和这个有什么区别”默认按已选技能列表进行解释或对比；如果用户要求执行技能任务但缺少 URL、当前页面、搜索词、附件或其他必要目标，模型再围绕已选技能追问缺失信息。

提示词上下文日志只打印可读预览，覆盖原始系统提示词和技能简介，完整技能文档仍会进入模型上下文，但不会在 `info` 日志中整段输出。

`SkillRuntimeService` 只解析本轮已选择的技能。目录化技能会读取 `SKILL.md`、`.codex-skill/skill.json` 和对象存储目录列表；历史 zip 技能保留内存扫描兼容；内置技能缺少 `storageKey` 时从类路径读取。运行时元数据只作为模型提示和诊断信息，不会把 `tools` 声明直接当成本地工具注册表使用。资源读取由 `SkillResourceBoundaryService` 统一保护，空路径、绝对路径、`.` 或 `..` 片段都会被拒绝。

`web-access` 是技能编码，不是本地工具编码。工具循环会用 `ChatToolSpecService` 返回的本轮可见工具 schema 做白名单校验，只有白名单内的 `tool_call` 才会进入 `ChatToolExecutionService`；被模型伪造出来的 skill code tool_call 会被记录为“模型伪工具调用已忽略”，然后通过系统上下文强调只忽略错误 tool_call，已选技能仍然有效，要求模型继续按技能说明回答，不能对用户说技能不可用或被忽略。

`chat_execution_step.runtime_context` 只服务运行回放和重新生成上下文恢复，不向前端普通步骤列表展示。重新生成时优先读取上一轮 `runtime_context`，缺失时回退解析用户消息前缀。

云端 `web-access` 技能每轮从对象存储下载到新的临时目录，不能依赖上一次生成的 `config.env`。下载完成后写入 `WEB_ACCESS_BROWSER=chrome`，配合工具进程里的 `CLAUDE_SKILL_DIR` 让 `check-deps.mjs` 能直接通过。若模型重复提交完全相同的工具和参数，后端会跳过重复执行并把上一轮结果回灌给模型，避免浏览器操作被反复执行到轮次上限。

`web-access` 的上游技能文档包含通用 `curl` 调用示例，但 CodingX 的 `bash` / `shell_command` 实际运行在 Windows PowerShell 中。技能上下文组装时会在该技能说明后追加项目内运行约束：先执行 `node "$env:CLAUDE_SKILL_DIR\scripts\check-deps.mjs"` 检查 Node、Chrome 和 CDP Proxy，GET 请求使用 `Invoke-RestMethod -Uri`，POST 请求使用 `Invoke-WebRequest -Method Post -Body`。用户要求“连接一下”某个 URL 时，模型必须先调用 `/targets` 枚举用户现有 Chrome 页面并复用 URL 或标题匹配的 tab；只有没有匹配目标时才调用 `/new` 创建后台 tab。拿到 `targetId` 后仍需用 `/info`、`/eval` 或 `/screenshot` 读取页面标题、最终 URL、正文摘要或截图证据；如果 `/info` 返回 `about:blank`、空标题或 URL 不匹配，必须重新选择目标或重新导航，不能把空白页当作成功结果。若模型只在正文里列出这些待执行命令但没有发起真实本地工具调用，后端会隐藏该计划；命令目标限定在 `localhost:3456`、`127.0.0.1:3456` 或 `check-deps.mjs` 时会自动兜底转成 `bash` 工具调用，避免把“正在执行”类文案当成最终回答。

旧表数据迁移时会先按 run 聚合 `task_skill` 与 `task_mcp`，写入 `chat_execution_step.metadata_json` 的 `skillCodes`、`mcpCodes` 字段；历史用户消息若尚无前缀，会补齐 `@skill`。随后 `task_expert.expert_code` 会补写到已有 `runtime_context` 的 `expertCode`，缺少上下文步骤的历史 run 会新增一条隐藏上下文步骤；迁移完成后删除 `task_artifact`、`task_event`、`task_mcp`、`task_skill`、`task_expert` 和 `task`。

## 测试与验证

- `mvn -DskipTests compile`
- `mvn -Dtest=ChatCapabilityMentionSupportTest test`
- `mvn -Dtest=ChatApplicationServiceTest test`
- `mvn -Dtest=ChatApplicationServiceTest#sendMessageRoutesSelectedSkillShortQuestionThroughModelInsteadOfStaticIntro test`
- `mvn -Dtest=ChatApplicationSearchFlowTest#sendMessageWithWebAccessSkillStillUsesSystemSearchEvidence,ChatApplicationServiceTest#sendMessageRoutesSelectedSkillShortQuestionThroughModelInsteadOfStaticIntro,ChatSkillContextServiceTest#buildSkillContextTreatsSelectedSkillAsActiveInstruction test`
- `mvn -Dtest=ChatWorkspaceQueryServiceTest test`
- `mvn -Dtest=ChatSkillContextServiceTest test`
- `mvn -Dtest=SkillRuntimeServiceTest test`
- `mvn -Dtest=ChatApplicationToolCallFlowTest#sendMessageSuppressesInitialCommandPlanAndRetriesToolCall test`
- `npm run test:run -- tests/views/ChatView.test.tsx -t 应在用户消息气泡内展示技能气泡且正文不重复技能标记`
- 运行后通过 PostgreSQL 确认旧任务表与绑定表不存在，最近用户消息 `content` 包含 `@web-access`，且 `chat_execution_step` 存在 `runtime_context` 元数据。
- 通过用户端 `/api/chat/stream` 携带 `skillCodes=web-access` 打开 `https://example.com`，工具步骤返回 `browser: ok`、`proxy: ready` 与 `title=Example Domain`。
