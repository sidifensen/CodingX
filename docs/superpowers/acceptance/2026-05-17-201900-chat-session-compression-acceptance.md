# Acceptance Criteria: 会话压缩闭环

**Spec:** `docs/superpowers/specs/2026-05-17-201800-chat-session-compression-design.md`  
**Date:** 2026-05-17  
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 聊天内存配置可通过 `app.chat.memory` 生效 | Logic | 设置 `summary-trigger-messages` 为自定义值并构造 `ConversationDigestService` | 达到阈值前不触发，达到阈值后触发 |
| AC-002 | 达到阈值时仅压缩“最近窗口之外”的历史区间 | Logic | 开启压缩，设置 `history-keep-turns=1`，构造 6 条历史消息 | 生成摘要的 `last_message_id` 指向窗口外最后一条消息，窗口内消息不进入摘要 |
| AC-003 | 已有摘要时再次压缩只处理新增区间，不重复汇总已压缩消息 | Logic | 摘要表存在 `last_message_id`，新增消息后再次触发压缩 | 新摘要内容追加新增区间，`last_message_id` 前移到新截止点 |
| AC-004 | 入模上下文命中摘要时应输出“摘要 system 消息 + 最近窗口原文” | Logic | 摘要表存在可用摘要，完整历史包含摘要前与摘要后消息 | 返回首条为 `system` 摘要消息，后续仅保留最近窗口原文 |
| AC-005 | 主聊天链路接入压缩上下文后原有功能不回归 | Logic | 运行 `ChatApplicationServiceTest`、`ChatApplicationIntentFlowTest`、`ChatApplicationSearchFlowTest`、`ChatApplicationMcpFlowTest` | 相关测试全部通过，且 LLM 链路调用新上下文构造入口 |
| AC-006 | 后端构建与全量测试通过 | Logic | 在 `backend` 目录执行构建命令 | `mvn compile`、`mvn test` 均成功 |
