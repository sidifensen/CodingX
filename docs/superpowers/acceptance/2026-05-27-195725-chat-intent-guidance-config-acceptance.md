# Acceptance Criteria: 聊天歧义引导配置化

**Spec:** `docs/superpowers/specs/2026-05-27-195725-chat-intent-guidance-config-design.md`
**Date:** 2026-05-27
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 歧义引导开关关闭时不返回澄清提示。 | Logic | `chat.intent.guidance.enabled=false`，候选包含两个分数接近的跨系统节点。 | `buildGuidancePrompt` 返回 `null`，不会调用 LLM 二次确认。 |
| AC-002 | 分数比值达到配置阈值时直接返回澄清提示。 | Logic | `enabled=true`，`ambiguity_score_ratio=0.8`，前两名分数为 `0.90` 和 `0.82`。 | 返回的提示包含两个候选完整路径，且不调用 LLM 二次确认。 |
| AC-003 | 分数比值处于边界区间时使用 LLM 二次确认。 | Logic | `enabled=true`，`ambiguity_score_ratio=0.8`，`ambiguity_margin=0.15`，前两名分数比值在 `[0.65,0.8)`。 | 当 LLM 返回 `{"ambiguous": true}` 时返回澄清提示；返回 `false` 时不提示。 |
| AC-004 | 用户问题显式包含系统名时跳过澄清。 | Logic | 候选跨两个系统，问题文本包含第一名或第二名所属系统名。 | `buildGuidancePrompt` 返回 `null`，避免重复询问用户已说明的范围。 |
| AC-005 | 歧义候选按系统维度去重并受最大选项数限制。 | Logic | 候选中同一系统有多条命中，`chat.intent.guidance.max_options=2`。 | 每个系统最多保留最高分候选，最终提示最多展示 2 个选项。 |
| AC-006 | 歧义引导运行时配置可以从系统配置表读取并有默认值。 | Logic | `RuntimeSettingService` 缓存包含或不包含 `chat.intent.guidance.*` 配置。 | 配置存在时读取数据库值；缺失时分别回退到 `true`、`0.8`、`0.15`、`6`。 |
| AC-007 | 数据库初始化和迁移脚本写入中文说明的歧义引导配置。 | Logic | 检查新增迁移脚本和 `backend/src/main/resources/db/init.sql`。 | 四个 `chat.intent.guidance.*` 配置均存在，`description` 为中文，`value_type` 与默认值匹配。 |
| AC-008 | 管理端系统配置页展示“歧义引导”中文分类。 | UI interaction | `AdminChatApi.listSettings` 返回 `categoryCode=chat.intent.guidance` 的配置。 | `Settings` 页面展示分类标题“歧义引导”，配置说明为中文。 |
