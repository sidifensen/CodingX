# AI 模型故障切换策略迁移设计

## 背景

用户要求将 `D:\code\ragent` 中模型故障切换策略迁移到 CodingX。CodingX 已有 `AiModelDispatchService`、`AiModelSelector`、`AiProviderHealthRegistry` 和首包缓冲组件，本次不机械复制包结构，而是在现有聊天模型接口上补齐 ragent 的核心策略。

## 目标

- 按 ragent 策略维护有序候选链：默认模型或深度思考模型优先，其次按 `priority` 和候选 ID 排序。
- 每个模型候选维护独立三态熔断：`CLOSED`、`OPEN`、`HALF_OPEN`，冷却后仅允许一个探测请求。
- 流式聊天在首包前失败、超时、无内容完成或 provider 客户端缺失时自动尝试下一个候选。
- 首包确认前的 thinking/content/tool/complete/error 事件必须缓冲，失败候选不得向下游泄漏半截输出。
- 调度运行态不能使用单例共享可变列表承载单次请求状态，避免并发请求互相污染尝试记录。

## 设计

保留 CodingX 当前 `common.support.ai` 路由边界。`AiModelSelector` 负责候选过滤、排序和 provider 配置装配；`AiModelDispatchService` 负责按候选链执行 provider 调用、首包探测、失败标记与 fallback；`AiProviderHealthRegistry` 继续承载三态熔断状态。

候选首选规则与 ragent 对齐：如果请求显式传入 `preferredModel`，该模型排第一；否则深度思考模式优先使用 `chat.deepThinkingModel`，普通模式使用 `chat.defaultModel`。当深度思考候选为空时，安全回退普通候选，避免聊天链路直接无模型可用。

流式执行规则保持首包探测：provider 返回 session 后等待 `firstPacketTimeoutMs`。首包前异常、超时、无内容完成都会取消当前 session、记录失败并尝试后续候选；首包成功后提交缓冲事件并等待 completion，completion 异常视为首包后失败，不再切换，以免用户已收到输出后被另一个模型接管。

## 测试

新增/更新单测覆盖：

- 默认模型和深度思考模型排序。
- provider 客户端缺失时跳过并继续 fallback。
- 首包前失败不泄漏缓冲内容。
- 熔断候选冷却前跳过，冷却后半开单探测。
- 并发请求的尝试记录互相隔离。

## 文档

更新 `docs/features/chat/ai-model-failover.md` 与功能索引，记录当前真实实现、核心流程和验证命令。
