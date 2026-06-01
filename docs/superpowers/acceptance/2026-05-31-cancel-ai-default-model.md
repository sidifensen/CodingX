# Acceptance Criteria: 取消 AI 默认模型设置

**Spec:** `docs/superpowers/specs/2026-05-31-222656-cancel-ai-default-model-design.md`
**Date:** 2026-05-31
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 普通聊天未传 `preferredModel` 时不再使用 `defaultModel` 抢占排序。 | Logic | `AiModelSelectorTest` 中存在低 priority 候选和高 priority 的历史默认候选。 | `selectChatCandidates(null, false)` 返回的首个候选是 priority 最小的候选。 |
| AC-002 | 深度思考聊天未传 `preferredModel` 时不再使用 `deepThinkingModel` 抢占排序。 | Logic | thinking 候选中存在 priority 更小候选和历史深度思考默认候选。 | `selectChatCandidates(null, true)` 返回的首个候选是 thinking 过滤后 priority 最小的候选。 |
| AC-003 | 显式 `preferredModel` 仍保留最高排序优先级。 | Logic | 请求传入的 `preferredModel` 对应启用候选且 provider 可用。 | `selectChatCandidates(preferredModel, false)` 返回的首个候选 ID 等于传入值。 |
| AC-004 | 新库初始化数据不再包含默认模型 setting 键。 | Logic | 读取 `backend/src/main/resources/db/init.sql`。 | 文件不包含 `ai.chat.default_model` 或 `ai.chat.deep_thinking_model`。 |
| AC-005 | 历史数据库升级会清理默认模型 setting 键。 | Logic | 读取 `backend/src/main/resources/db/migration` 下 SQL 迁移。 | 至少一条迁移同时命中两个默认模型键，并把它们标记为 `deleted = 1`。 |
| AC-006 | 管理端设置页不再提供默认模型专用入口。 | UI interaction | 管理端设置接口返回样例中不包含默认模型键。 | 打开设置页并进入 AI 基础配置时，看不到“模型路由默认模型ID”和“模型路由深度思考模型ID”。 |
| AC-007 | 功能文档描述候选池 priority 是默认路由顺序来源。 | Logic | 读取 `docs/features/chat/ai-model-failover.md` 和 `docs/features/chat/ai-routing-defaults.md`。 | 文档不再声明默认模型指针参与排序，并明确未显式选择时按候选池 priority 排序。 |
