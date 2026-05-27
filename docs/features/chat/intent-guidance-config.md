# 聊天歧义引导配置

## 功能用途

聊天意图识别命中多个接近候选时，系统先向用户展示澄清选项，避免在用户未明确系统或主题范围时直接进入搜索、MCP 或直答链路。

## 使用入口

- 用户入口：聊天页发送含糊问题，例如“系统介绍是什么”。
- 管理入口：管理端“系统配置”页中的“歧义引导”分类。

## 核心流程

1. `ConversationIntentService` 完成意图候选识别后调用 `ConversationIntentGuidanceService`。
2. `ConversationIntentAmbiguityDetector` 读取系统配置，关闭时直接跳过。
3. 检测器先过滤低于 `0.35` 的低置信候选，再按系统维度去重，每个系统只保留最高分节点。
4. 仅保留与第一名同名的主题候选；不同主题或不同搜索策略不触发澄清。
5. 如果用户问题已包含系统名，或候选分数差距足够大，则不触发澄清。
6. 分数比值达到阈值时直接澄清；处于边界区间时调用 LLM 二次确认。
7. 命中歧义后记录 `歧义引导触发` INFO 日志，包含问题、主题、候选数和候选摘要。
8. 渲染 `guidance-prompt.st`，返回给聊天主链路作为澄清回复。

## 配置项

配置存储在 `setting` 表，说明字段为中文，管理端可直接编辑：

| 配置键 | 默认值 | 类型 | 说明 |
|---|---:|---|---|
| `chat.intent.guidance.enabled` | `true` | `BOOLEAN` | 是否启用聊天歧义引导 |
| `chat.intent.guidance.ambiguity_score_ratio` | `0.8` | `DECIMAL` | 歧义引导分数比值阈值 |
| `chat.intent.guidance.ambiguity_margin` | `0.15` | `DECIMAL` | 歧义引导边界缓冲宽度 |
| `chat.intent.guidance.max_options` | `6` | `INTEGER` | 歧义引导最大候选数量 |

## 关键文件

- `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationIntentGuidanceService.java`：澄清提示编排和渲染。
- `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationIntentAmbiguityDetector.java`：歧义候选检测、阈值判断和 LLM 二次确认。
- `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationIntentPathResolver.java`：意图节点路径、系统名和系统去重标识解析。
- `backend/src/main/java/com/codingx/chat/application/service/support/RuntimeSettingService.java`：运行时配置读取。
- `backend/src/main/resources/db/migration/V20260527_200000__add_chat_intent_guidance_settings.sql`：新增系统配置数据迁移。
- `frontend/admin/src/pages/Settings.tsx`：系统配置页中文分类展示。

## 边界说明

“天气怎么样”这类问题只命中天气 MCP 一个意图，不属于跨系统同名主题歧义；如果缺少城市，会走天气 MCP 参数澄清，不会触发歧义引导配置。

## 测试与验证

- `mvn "-Dtest=ConversationIntentGuidanceServiceTest" test`
- `mvn "-Dtest=RuntimeSettingServiceTest" test`
- `mvn "-Dtest=ChatRuntimePersistenceStructureTest#chatIntentGuidanceRuntimeSettingsAreSeeded" test`
- `npm run test:run -- Settings.test.tsx`
