# Acceptance Criteria: Chat Model Engine Alignment

**Spec:** `docs/superpowers/specs/2026-05-15-191448-chat-model-engine-alignment-design.md`
**Date:** 2026-05-15
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 路由层在存在 `preferredModel` 时优先尝试指定模型 | Logic | 构造两个启用候选，指定其中一个为 `preferredModel` | 最近一次尝试顺序中指定模型排第一 |
| AC-002 | 深度思考模式下优先筛出 `supportsThinking=true` 的候选 | Logic | 候选列表同时包含支持和不支持思考的模型 | 选择结果仅包含支持思考候选；若不存在则安全回退到普通候选 |
| AC-003 | 模型健康注册表在连续失败达到阈值后进入 `OPEN` 状态 | Logic | 设置失败阈值为 2 并连续标记两次失败 | 第二次失败后 `allowCall` 返回 false，直到冷却时间结束 |
| AC-004 | 熔断窗口结束后只允许一个半开探测请求通过 | Logic | 构造已进入 `OPEN` 状态且冷却已到期的模型 | 首个 `allowCall` 返回 true，第二个并发/连续探测在成功前返回 false |
| AC-005 | 首包前发生错误时，路由层不会向下游泄漏已缓冲事件，并自动 fallback 到下一候选 | Logic | 第一个候选先发 thinking 后报错，第二个候选正常返回 content | 下游只收到第二个候选的输出，且流程最终完成 |
| AC-006 | 首包超时或无内容完成时，路由层会取消当前会话并继续尝试下一候选 | Logic | 第一个候选不产生有效首包或直接完成，第二个候选正常 | 下游最终收到第二个候选的内容与完成事件 |
| AC-007 | 命中模型的 `provider` 与 `model` 元信息会传到旧领域接口桥接层 | Logic | 使用路由聊天客户端发起一次成功调用 | 旧领域 `StreamHandler` 收到与候选一致的 provider/model 元信息 |
| AC-008 | 所有候选都失败时，聊天链路通过 `onError` 收口而不是只抛出未处理异常 | Logic | 所有候选都返回首包前失败 | 下游 `onError` 被调用，测试可断言错误消息包含“all candidates failed”或等效语义 |
| AC-009 | 旧配置模式下未显式声明 `chat.candidates` 时，系统仍能生成默认真实候选与 stub 候选 | Logic | 仅提供旧式 `app.ai.provider/base-url/api-key/chat-model` 配置 | 选择器返回至少一个真实候选和一个 `stub` 候选 |
| AC-010 | 后端模型引擎改造后能通过项目回归验证 | Logic | 本轮代码改动完成 | `mvn compile` 与 `mvn test` 均退出 0 |
