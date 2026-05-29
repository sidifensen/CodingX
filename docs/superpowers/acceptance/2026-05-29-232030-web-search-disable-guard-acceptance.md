# Acceptance Criteria: 系统联网搜索禁用守卫

**Spec:** `docs/superpowers/specs/2026-05-29-232030-web-search-disable-guard-design.md`
**Date:** 2026-05-29
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 系统联网搜索关闭后，搜索型问题不得执行内置搜索链路。 | Logic | `runtimeSettingService.webSearchEnabled()` 返回 `false`，意图路由返回 `SEARCH`。 | `WebSearchExecutionService.search`、`ChatExecutionStepRepository.save`、`SearchReferenceCollector.collect`、`DocumentArtifactService.createDocxArtifact` 均不被调用，助手仍通过模型流式完成。 |
| AC-002 | 选中 `web-access` 技能后，搜索型问题不得执行系统内置搜索。 | Logic | `runtimeSettingService.webSearchEnabled()` 返回 `true`，`skillCodes` 包含 `web-access`，意图路由返回 `SEARCH`。 | 不调用系统搜索链路，模型系统消息包含 `web-access` 技能上下文。 |
| AC-003 | 系统搜索开启且未选择 `web-access` 时，既有搜索流程保持可用。 | Logic | `runtimeSettingService.webSearchEnabled()` 返回 `true`，意图路由返回 `SEARCH`。 | 搜索服务被调用，搜索引用和搜索整理文档仍按既有流程创建。 |
