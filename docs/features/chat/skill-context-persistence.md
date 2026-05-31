# 聊天技能上下文持久化

## 功能用途

用户在聊天输入区选择技能后，技能选择同时具备两层语义：一是作为可追溯的消息内容前缀写入 `chat_message.content`，二是把对应 `SKILL.md` 注入本轮模型系统上下文，避免短问题被普通闲聊或关于助手意图吞掉。

## 使用入口

聊天页选择技能或输入 `@skill` 后发送消息。前端仍通过 `skillCodes` 参数声明当前选择；后端保存用户消息时统一写成 `@web-access 这是啥` 这类可读内容，消息回放接口再从内容前缀解析 `skillCodes` 给前端展示技能 chip。

## 核心流程

1. `ChatStreamController` 合并显式 `skillCodes` 与结构化技能命令，生成 `SendChatMessageCommand`。
2. `ChatApplicationService` 合并正文前缀与请求参数里的技能码，用户消息落库前用 `ChatCapabilityMentionSupport` 加上 `@skill` 前缀。
3. 当本轮已选技能且用户只问“这是什么”“这是啥”“有什么用”等短句时，`ChatApplicationService` 直接用技能简介生成回答，避免模型把短指代误判为缺少页面、链接或附件。
4. 改写、意图识别、标题、摘要和模型历史使用剥离前缀后的纯正文，避免把 `@skill` 当自然语言。
5. `ChatSkillContextService` 读取已选技能根级 `SKILL.md` 并追加到系统提示，日志会输出技能上下文是否生效；当用户正文使用“这个”“这些”“有什么区别”等指代，或只问“这是什么”“这是啥”等短句时，提示词会要求模型先按已选技能理解。
6. 模型工具调用只允许执行本轮真实暴露的工具 schema；若模型把 `web-access` 这类 skill code 伪造成 tool_call，后端仅忽略这个错误的 tool_call，并回灌“已选技能仍然有效、技能编码不是工具名”的约束。
7. `SkillLocalCacheService` 为云端临时下载的 `web-access` 写入 `WEB_ACCESS_BROWSER=chrome`，避免每轮新目录都要求用户重新选择浏览器。
8. 本轮技能、MCP、专家上下文写入隐藏的 `chat_execution_step(step_type=runtime_context)`，旧的 `task_skill`、`task_mcp`、`task_event`、`task_artifact` 表不再使用。
9. 前端用户消息展示时只在 chip 里显示技能码，正文剥离开头 `@skill`，复制、编辑和分享预览也使用可见正文。

## 关键文件

- `backend/src/main/java/com/codingx/chat/application/service/ChatCapabilityMentionSupport.java`：格式化、解析和剥离消息正文中的技能前缀。
- `backend/src/main/java/com/codingx/chat/application/service/ChatRunContextStepSupport.java`：把运行能力上下文编码到 `chat_execution_step`。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：落库用户消息、注入技能上下文、隐藏运行上下文步骤。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatWorkspaceQueryService.java`：从 `runtime_context` 读取当前技能与 MCP，并对普通步骤列表隐藏该内部步骤。
- `backend/src/main/java/com/codingx/skill/application/service/SkillLocalCacheService.java`：下载 skill 临时目录，并为 `web-access` 补齐默认浏览器配置。
- `frontend/user/src/views/ChatView.tsx`：展示层剥离用户消息开头技能标记，避免和技能 chip 重复。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：发送前解析 `@skill` 前缀，支持与后端一致的技能编码字符集。
- `backend/src/main/resources/db/migration/V20260529_200500__migrate_task_context_to_chat_execution_step.sql`：迁移旧任务能力上下文并删除旧表。
- `backend/src/main/resources/db/schema.sql`：基线结构不再包含旧任务绑定和任务产物/事件表。

## 关键逻辑

`chat_message.content` 是用户可审计输入，不再把技能选择隐藏在独立绑定表里。模型侧不能直接使用这个持久化内容，因为 `@web-access 这是啥` 会干扰改写、意图和回答，因此所有入模历史都会经过 `ChatCapabilityMentionSupport.toPlainAiMessage` 剥离前缀。

由于入模用户正文会剥离开头 `@skill`，技能系统提示必须补足指代关系：`@web-access 这是啥` 入模正文虽然是“这是啥”，但“这个”默认指向本轮已选的 `web-access`；`@web-access 这是什么` 会按“询问已选技能本身”处理，后端优先从 `SKILL.md` 的 `description` 或技能配置简介生成确定性直答；多个技能同时选中时，“这个和这个有什么区别”默认按已选技能列表进行解释或对比。

提示词上下文日志只打印可读预览，覆盖原始系统提示词和技能简介，完整技能文档仍会进入模型上下文，但不会在 `info` 日志中整段输出。

`web-access` 是技能编码，不是本地工具编码。工具循环会用 `ChatToolSpecService` 返回的本轮可见工具 schema 做白名单校验，只有白名单内的 `tool_call` 才会进入 `ChatToolExecutionService`；被模型伪造出来的 skill code tool_call 会被记录为“模型伪工具调用已忽略”，然后通过系统上下文强调只忽略错误 tool_call，已选技能仍然有效，要求模型继续按技能说明回答，不能对用户说技能不可用或被忽略。

`chat_execution_step.runtime_context` 只服务运行回放和重新生成上下文恢复，不向前端普通步骤列表展示。重新生成时优先读取上一轮 `runtime_context`，缺失时回退解析用户消息前缀。

云端 `web-access` 技能每轮从对象存储下载到新的临时目录，不能依赖上一次生成的 `config.env`。下载完成后写入 `WEB_ACCESS_BROWSER=chrome`，配合工具进程里的 `CLAUDE_SKILL_DIR` 让 `check-deps.mjs` 能直接通过。若模型重复提交完全相同的工具和参数，后端会跳过重复执行并把上一轮结果回灌给模型，避免浏览器操作被反复执行到轮次上限。

旧表数据迁移时会先按 run 聚合 `task_skill` 与 `task_mcp`，写入 `chat_execution_step.metadata_json` 的 `skillCodes`、`mcpCodes`、`expertCode` 字段；历史用户消息若尚无前缀，会补齐 `@skill`。迁移完成后删除 `task_artifact`、`task_event`、`task_mcp`、`task_skill`。

## 测试与验证

- `mvn -DskipTests compile`
- `mvn -Dtest=ChatCapabilityMentionSupportTest test`
- `mvn -Dtest=ChatApplicationServiceTest test`
- `mvn -Dtest=ChatWorkspaceQueryServiceTest test`
- `npm run test:run -- tests/views/ChatView.test.tsx -t 应在用户消息气泡内展示技能气泡且正文不重复技能标记`
- 运行后通过 PostgreSQL 确认四张旧表不存在，最近用户消息 `content` 包含 `@web-access`，且 `chat_execution_step` 存在 `runtime_context` 元数据。
- 通过用户端 `/api/chat/stream` 携带 `skillCodes=web-access` 打开 `https://example.com`，工具步骤返回 `browser: ok`、`proxy: ready` 与 `title=Example Domain`。
