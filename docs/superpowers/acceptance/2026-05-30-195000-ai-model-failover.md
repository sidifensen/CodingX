# Acceptance Criteria: AI 模型故障切换策略

**Spec:** `docs/superpowers/specs/2026-05-30-195000-ai-model-failover-design.md`
**Date:** 2026-05-30
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 普通聊天未指定模型时优先选择 `chat.defaultModel`。 | Logic | 配置多个启用候选且 `defaultModel` 指向非最低 priority 候选。 | `AiModelSelector.selectChatCandidates(null, false)` 返回列表首项 ID 等于 `defaultModel`。 |
| AC-002 | 深度思考聊天未指定模型时优先选择 `chat.deepThinkingModel`。 | Logic | 配置多个支持 thinking 的候选且 `deepThinkingModel` 指向非最低 priority 候选。 | `AiModelSelector.selectChatCandidates(null, true)` 返回列表首项 ID 等于 `deepThinkingModel`。 |
| AC-003 | 首包前失败的候选不会向下游泄漏缓冲输出。 | Logic | 第一个 provider 首包前发送 thinking 后报错，第二个 provider 正常输出。 | 下游只收到第二个 provider 的正文增量，尝试顺序包含两个 provider。 |
| AC-004 | 熔断中的候选在冷却结束前不参与调度。 | Logic | 第一个候选失败阈值为 1，第二个候选正常。 | 第二次请求的尝试顺序只包含第二个 provider。 |
| AC-005 | provider 客户端缺失时继续尝试后续候选。 | Logic | 候选列表第一项 provider 无客户端，第二项 provider 有客户端。 | 请求成功输出第二项内容，尝试顺序只记录实际调用的 provider。 |
| AC-006 | 并发请求的调度尝试记录互不污染。 | Logic | 两个线程同时调用同一个 `AiModelDispatchService` 实例。 | 每个请求上下文记录各自 provider 顺序，`getLastAttemptedProviders()` 返回某一次完整调用快照且不出现交叉残留。 |
