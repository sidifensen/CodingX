# AI 路由默认策略

## 功能用途

统一约束后端聊天模型的默认路由策略，确保文本默认走硅基流动提供的 DeepSeek，图片附件优先走硅基流动提供的千问视觉模型，同时保留百炼与其他候选作为故障回退。

## 使用入口

后端启动后由 `DynamicAiProperties` 和 `DynamicAiRoutingProperties` 从数据库 `setting` 中读取 AI 运行时配置。`application.yml` 只保留 provider endpoint 与候选池的最小静态骨架，作为数据库缺失时的安全回退。

## 核心流程

1. `ai.provider`、`ai.base_url`、`ai.api_key`、`ai.chat_model`、`ai.selection.*` 和 `ai.chat.default_model` / `ai.chat.deep_thinking_model` 都由系统配置表控制。
2. provider 级 `chat` endpoint 使用 `ai.providers.<provider>.endpoints.chat` 键位控制，不再只能写死在 `application.yml`。
3. 候选池按 `ai.chat.candidates.<slot>.*` 扁平键组织，当前默认优先级是硅基流动 DeepSeek、硅基流动千问视觉、百炼文本、百炼思考、百炼视觉回退。
4. 当请求包含图片附件时，`AiModelSelector` 会优先筛选 `supports_vision=true` 的数据库候选，因此会先命中硅基流动千问视觉模型。
5. `init.sql` 与历史迁移脚本会同步写入 endpoint 和候选池键位，避免旧环境只有默认模型 ID、缺失 provider 细项。

## 关键文件

- `backend/src/main/java/com/codingx/config/DynamicAiProperties.java`：聚合 provider、endpoint 和候选池系统配置。
- `backend/src/main/resources/application.yml`：保留 AI 静态骨架 fallback。
- `backend/src/main/resources/db/init.sql`：初始化数据库运行时默认模型、endpoint 与候选池键位。
- `backend/src/main/resources/db/migration/V20260528_155500__switch_ai_routing_defaults_to_siliconflow_deepseek.sql`：同步历史环境默认模型。
- `backend/src/main/resources/db/migration/V20260528_204500__seed_full_ai_system_settings.sql`：补齐 endpoint 与候选池系统配置键位。
- `backend/src/test/java/com/codingx/config/AiRoutingDefaultsConfigTest.java`：校验 YAML、初始化 SQL 与迁移脚本的一致性。
- `backend/src/test/java/com/codingx/support/ai/AiModelSelectorTest.java`：校验视觉候选和优先级选择逻辑。

## 关键逻辑

SiliconFlow 与百炼都走 `OpenAiCompatibleChatClient`，因此这次调整主要体现在数据库候选池、provider endpoint 与默认模型 ID 的动态聚合。视觉模型候选必须保留 `supports_vision=true`，否则图片请求不会优先切到千问视觉模型。数据库 `setting` 会覆盖 YAML 骨架，因此每次修改默认路由都必须同步更新 `init.sql` 与迁移脚本，避免“新环境正确、老环境错误”的分裂状态。

## 测试与验证

- `mvn -Dtest=AiRoutingDefaultsConfigTest test`
- `mvn -Dtest=AiModelSelectorTest test`
