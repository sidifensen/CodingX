# Chat Model Engine Alignment Design

**Date:** 2026-05-15
**Scope:** `CodingX` 聊天运行时中的模型引擎补齐，对齐 `ragent` 已经验证过的模型调度、首包探测、健康检查与自动降级机制。

## 1. 背景

当前 `CodingX` 的聊天主链路已经接入了统一请求对象、OpenAI 风格流解析器、基础 provider 抽象，以及最小可运行的 fallback 骨架。但与 `D:\code\ragent` 对照后，模型引擎仍有关键缺口：

- 当前路由粒度是 `provider`，不是 `model target`
- 当前健康状态是简单失败计数，不是三态熔断器
- 当前所谓“首包缓冲”只是本地 delta 缓冲，没有“启动流式 -> 等待首包 -> 首包失败回退”的完整握手
- 当前调用失败主要通过抛异常传播，不能稳定回落到消息失败收口
- 当前助手消息不会落 `provider` / `model` 元信息，后续回放和 Trace 观察不足

这意味着 `docs/project/chat-search-runtime-refactor` 已写入的“模型调度、首包探测、健康检查、自动降级，模型故障不影响服务”在代码里还没有真正闭环。

## 2. 本轮目标

本轮只补齐聊天模型引擎，不扩散到搜索、前端和知识库链路。

目标包括：

1. 将模型选择从“provider Bean 顺序”升级为“模型候选列表 + 首选模型优先 + 深度思考过滤”
2. 将健康状态从“失败计数”升级为“CLOSED / OPEN / HALF_OPEN” 三态熔断器
3. 为流式调用引入“可取消会话 + 首包等待器 + 首包前事件缓冲”机制
4. 仅在“首包前失败”时自动 fallback，避免把半截脏流暴露给前端
5. 将最终命中的 `provider` / `model` 回传到聊天应用层并持久化到 `chat_message`
6. 对齐配置结构，使 `CodingX` 能表达 `ragent` 类似的 `providers / chat.candidates / selection` 配置

## 3. 明确不做

本轮不做：

- Redis 排队限流的 `ragent` 式完整迁移
- Trace 注解 + 切面自动采集迁移
- 前端 `cancel/reject/error/thinking` 事件消费补齐
- 多供应商的全量 provider 客户端迁移

这些项会在计划里标为后续缺口，不与本轮模型引擎改造耦合。

## 4. 设计方案

### 4.1 配置层

保留现有 `app.ai` 前缀，但扩展为两层：

- 兼容层：继续支持当前 `provider/base-url/api-key/chat-model` 旧配置，避免本地环境立即失效
- 新模型层：新增 `providers`、`chat.default-model`、`chat.deep-thinking-model`、`chat.candidates`、`selection.failure-threshold`、`selection.open-duration-ms`、`selection.first-packet-timeout-ms`

若未配置 `chat.candidates`，系统自动从旧配置合成一个默认 DeepSeek 候选，并追加本地 `stub` 候选作为保底。

### 4.2 模型选择层

新增 `CodingX` 自有模型目标抽象：

- `AiModelTarget`：封装模型 id、候选配置、provider 配置
- `AiModelSelector`：负责根据 `preferredModel`、`thinkingEnabled`、`priority` 选择候选列表

行为规则：

- 显式指定的 `preferredModel` 优先排第一
- 深度思考模式只保留 `supportsThinking=true` 的候选；若没有，则回退到普通候选列表
- `enabled=false` 的候选直接过滤
- provider 缺失配置时跳过并记录日志

### 4.3 健康状态层

保留 `AiProviderHealthRegistry` 名称以减少改动面，但语义升级为“模型健康注册表”。

状态机：

- `CLOSED`：正常放行，累计失败计数
- `OPEN`：达到阈值后熔断，在 `openDurationMs` 内拒绝调用
- `HALF_OPEN`：冷却期结束后仅放行一个探测请求

状态流转完全参考 `ragent`：

- `CLOSED -> OPEN`：连续失败达到阈值
- `OPEN -> HALF_OPEN`：熔断窗口到期
- `HALF_OPEN -> CLOSED`：探测成功
- `HALF_OPEN -> OPEN`：探测失败

### 4.4 流式握手层

新增两个关键部件：

- `FirstTokenAwaiter`：等待首个有效流事件（content 或 thinking）
- 强化后的 `FirstTokenBufferingHandler`：首包前缓冲 `thinking/content/complete/error`，成功后统一提交

调用流程：

1. 选择第一个候选模型
2. 启动 provider 流式会话，立即返回可取消句柄与完成 Future
3. 在路由层等待首包结果
4. 若首包成功，提交缓冲并等待整条流完成
5. 若首包超时 / 首包前错误 / 无内容完成，则取消当前会话并切下一个模型
6. 若所有候选都失败，通过 `handler.onError(...)` 收口，而不是只抛异常

### 4.5 Provider 抽象层

现有 `AiProviderClient` 从“自带 candidate 的 provider”改成“按目标模型执行的 provider 客户端”。

新的最小契约：

- `provider()`：声明客户端负责的 provider 名称
- `streamChat(request, target, handler)`：按目标模型发起调用并返回可取消会话

当前保留两类实现：

- `DeepSeekOkHttpChatClient`
- `StubAiChatClient`

这样可以在不一次性迁移百炼 / SiliconFlow / Ollama 的前提下，先把路由骨架做对。

### 4.6 应用层回传

`AiStreamHandler` 与领域层 `AiChatClient.StreamHandler` 扩展元信息回调：

- `onMetadata(provider, model)`
- `onThinkingDelta(delta)` 先只做向下兼容，不强制前端消费

聊天应用层将记录命中模型的 `provider` / `model`，并在最终 assistant message 落库时写入。

## 5. 验证策略

本轮验收以单元测试和后端回归为主：

- 模型选择：首选模型优先、深度思考过滤、旧配置回退候选生成
- 熔断器：三态转换、半开单探测
- 首包探测：首包前错误 fallback、超时 fallback、首包成功后不泄露脏流
- 路由桥接：命中模型元信息能传到旧领域接口
- 回归：`mvn compile`、`mvn test`

## 6. 本轮之外仍待完成的缺口

通过与 `ragent` 和当前计划文档对照，当前仍明确未完成：

1. Redis 排队限流仍是进程内 `ConversationQueueGate`
2. Trace 仍是手工记录，没有注解 + AOP 自动采集
3. 前端仍未完整消费 `cancel/reject/error/thinking`
4. 会话删除、首屏示例问题、思考内容展示尚未收口

这些缺口会在实施计划中单列，不与本轮模型引擎改造混写。
