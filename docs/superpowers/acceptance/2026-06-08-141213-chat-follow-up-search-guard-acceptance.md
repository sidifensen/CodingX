# Acceptance Criteria: Chat Follow-up Search Guard

**Spec:** `docs/superpowers/specs/2026-06-08-141213-chat-follow-up-search-guard-design.md`
**Date:** 2026-06-08
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 代码生成会话中第二轮“丰富一下”即使被意图服务判为 `SEARCH` 也不得执行联网搜索。 | Logic | 会话历史包含上一轮 HTML/文件写入产物；`ConversationIntentService.route("丰富一下", false)` 返回 `SEARCH`。 | `WebSearchExecutionService.search(...)` 未被调用，`SearchReferenceCollector.collect(...)` 未被调用，`DocumentArtifactService.createDocxArtifact(...)` 未被调用。 |
| AC-002 | 被保护的短指令仍应进入普通模型生成，不能被澄清或提前直答短路。 | Logic | 与 AC-001 相同，`AiChatClient` 返回“已继续丰富页面”。 | 后端保存助手消息并发布 `publishAssistantCompleted(..., "已继续丰富页面", ...)`。 |
| AC-003 | 显式搜索请求不受保护影响，仍按现有搜索证据链执行。 | Logic | 用户输入包含“搜一下”“搜索”“查询”“最新”等显式检索词，意图服务返回 `SEARCH`，`web_search.enabled=true`。 | 既有 `ChatApplicationSearchFlowTest#sendMessageInvokesSearchFlowForSearchIntent` 继续通过，搜索服务、引用收集和文档产物均被调用。 |
| AC-004 | 系统搜索关闭守卫的现有语义不被破坏。 | Logic | `web_search.enabled=false` 且意图服务返回 `SEARCH`。 | 既有 `ChatApplicationSearchFlowTest#sendMessageSkipsSearchFlowWhenWebSearchDisabled` 继续通过，搜索服务不会被调用且隐藏运行上下文仍落库。 |

## Self Review

- 所有标准均为可执行单测或既有回归测试。
- 每个预期结果都指定了可观测调用或输出。
- 本次后端逻辑调整不涉及浏览器 UI，因此不设置 UI 验收项。
