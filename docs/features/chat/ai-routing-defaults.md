# AI 路由默认策略

## 功能用途

统一约束后端聊天模型的默认路由策略，确保文本默认走硅基流动提供的 DeepSeek，图片附件优先走硅基流动提供的千问视觉模型，同时保留百炼与其他候选作为故障回退。

## 使用入口

后端启动后读取 `backend/src/main/resources/application.yml` 的 `app.ai` 配置，并由数据库 `setting` 中的 `ai.chat.default_model`、`ai.chat.deep_thinking_model` 对默认路由进行运行时覆盖。

## 核心流程

1. `app.ai.provider` 默认设置为 `siliconflow`，顶层 `chat-model` 默认指向硅基流动的 `deepseek-ai/DeepSeek-V4-Flash`。
2. `app.ai.chat.default-model` 默认使用候选 ID `siliconflow-deepseek-v4-flash`，深度思考默认使用 `siliconflow-deepseek-v4-flash-thinking`。
3. 候选池按优先级先放硅基流动的 DeepSeek 与千问视觉模型 `Qwen/Qwen3.5-122B-A10B`，再放百炼文本、百炼思考和百炼视觉回退。
4. 当请求包含图片附件时，`AiModelSelector` 会优先筛选 `supportsVision=true` 的候选，因此会先命中硅基流动千问视觉模型。
5. `init.sql` 与历史迁移脚本会把数据库运行时默认模型同步改成 SiliconFlow DeepSeek，避免旧环境继续沿用旧默认值。

## 关键文件

- `backend/src/main/resources/application.yml`：声明 AI provider、候选池顺序和视觉优先候选。
- `backend/src/main/resources/db/init.sql`：初始化数据库运行时默认模型。
- `backend/src/main/resources/db/migration/V20260528_090000__switch_ai_routing_defaults_to_deepseek.sql`：同步历史环境默认模型。
- `backend/src/test/java/com/codingx/config/AiRoutingDefaultsConfigTest.java`：校验 YAML、初始化 SQL 与迁移脚本的一致性。
- `backend/src/test/java/com/codingx/support/ai/AiModelSelectorTest.java`：校验视觉候选和优先级选择逻辑。

## 关键逻辑

SiliconFlow 与百炼都走 `OpenAiCompatibleChatClient`，因此这次调整主要体现在候选顺序、默认模型 ID 与运行时覆盖值，不需要修改 provider 客户端实现。视觉模型候选必须保留 `supportsVision=true`，否则图片请求不会优先切到千问视觉模型。数据库 `setting` 会覆盖 YAML 默认值，所以每次修改默认路由都必须同步更新 `init.sql` 与迁移脚本，避免“新环境正确、老环境错误”的分裂状态。

## 测试与验证

- `mvn -Dtest=AiRoutingDefaultsConfigTest test`
- `mvn -Dtest=AiModelSelectorTest test`
