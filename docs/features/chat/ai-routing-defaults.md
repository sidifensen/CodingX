# AI 路由默认策略

## 功能用途

统一约束后端聊天模型的默认路由策略，确保未显式选模型时只按候选池优先级选择默认调用目标。当前候选优先级让文本默认走硅基流动提供的 DeepSeek，图片附件优先走硅基流动提供的千问视觉模型，同时保留百炼与其他候选作为故障回退。

## 使用入口

后端启动后由 `DynamicAiProperties` 和 `DynamicAiRoutingProperties` 从数据库 `setting` 中读取 AI 运行时配置。`application.yml` 只保留 provider endpoint 与候选池的最小静态骨架，作为数据库缺失时的安全回退。

## 核心流程

1. `ai.provider`、`ai.base_url`、`ai.api_key`、`ai.chat_model`、`ai.selection.*` 和候选池 `ai.chat.candidates.<slot>.*` 都由系统配置表控制。
2. provider 级 `chat` endpoint 使用 `ai.providers.<provider>.endpoints.chat` 键位控制，不再只能写死在 `application.yml`。
3. 候选池按 `ai.chat.candidates.<slot>.*` 扁平键组织，当前默认优先级是硅基流动 DeepSeek、硅基流动千问视觉、百炼文本、百炼思考、百炼视觉回退；普通请求会先尝试非 thinking 候选，再把 thinking 候选作为后续 fallback。
4. 当请求包含图片附件时，`AiModelSelector` 会优先筛选 `supports_vision=true` 的数据库候选，因此会先命中硅基流动千问视觉模型。
5. 路由层默认等待首包 15 秒；超时后取消当前 provider 的底层 HTTP Call，并切换到下一个候选，避免旧慢连接继续占用后台读流资源。
6. `init.sql` 与历史迁移脚本会同步写入 endpoint、候选池和首包超时键位；历史默认模型指针会被清理，避免旧环境继续通过废弃键位改变默认顺序。

## 关键文件

- `backend/src/main/java/com/codingx/config/DynamicAiProperties.java`：聚合 provider、endpoint 和候选池系统配置。
- `backend/src/main/java/com/codingx/common/support/ai/AiModelDispatchService.java`：执行候选排序、首包等待、失败标记与自动切换。
- `backend/src/main/java/com/codingx/chat/infrastructure/ai/OpenAiCompatibleChatClient.java`：执行百炼与 SiliconFlow 的 OpenAI 兼容流式调用，并在取消时中断 OkHttp Call。
- `backend/src/main/java/com/codingx/chat/infrastructure/ai/DeepSeekOkHttpChatClient.java`：执行 DeepSeek 流式调用，并在取消时中断 OkHttp Call。
- `backend/src/main/resources/application.yml`：保留 AI 静态骨架 fallback。
- `backend/src/main/resources/db/init.sql`：初始化数据库运行时 provider、endpoint 与候选池键位。
- `backend/src/main/resources/db/migration/V20260531_222700__remove_ai_default_model_settings.sql`：清理历史环境默认模型指针。
- `backend/src/main/resources/db/migration/V20260528_204500__seed_full_ai_system_settings.sql`：补齐 endpoint 与候选池系统配置键位。
- `backend/src/main/resources/db/migration/V20260529_011500__reduce_ai_first_packet_timeout.sql`：将历史环境首包等待窗口同步为 15 秒。
- `backend/src/test/java/com/codingx/config/AiRoutingDefaultsConfigTest.java`：校验 YAML、初始化 SQL 与迁移脚本的一致性。
- `backend/src/test/java/com/codingx/support/ai/AiModelSelectorTest.java`：校验视觉候选和优先级选择逻辑。
- `backend/src/test/java/com/codingx/chat/infrastructure/ai/OpenAiCompatibleChatClientTest.java`：校验取消会话会快速结束底层 HTTP 调用。

## 关键逻辑

SiliconFlow 与百炼都走 `OpenAiCompatibleChatClient`，DeepSeek 走 `DeepSeekOkHttpChatClient`。路由层以模型候选 ID 维度累计失败，达到阈值后打开熔断窗口；首包超时也会计为失败并尝试后续候选。provider 的 `AiStreamSession.cancel()` 必须取消真实 OkHttp `Call`，否则首包超时后虽然主请求会继续切换，旧 provider 连接仍可能在后台挂到 `ai.read_timeout_ms`。视觉模型候选必须保留 `supports_vision=true`，否则图片请求不会优先切到千问视觉模型。普通请求的 `priority` 只在非 thinking 候选桶内决定顺序，避免 thinking 候选因运行时配置被调高而误触发深度思考；若 provider 仍返回 `reasoning_content`，调度层与客户端会在 `thinkingEnabled=false` 时丢弃该增量，且被丢弃的 reasoning 不算首包成功。数据库 `setting` 会覆盖 YAML 骨架，因此每次修改候选默认顺序都必须同步更新候选 `priority`、`init.sql` 与迁移脚本，避免“新环境正确、老环境错误”的分裂状态。

## 测试与验证

- `mvn -Dtest=AiRoutingDefaultsConfigTest test`
- `mvn -Dtest=AiModelSelectorTest test`
- `mvn -Dtest=OpenAiCompatibleChatClientTest#cancelStopsUnderlyingHttpCallPromptly test`
