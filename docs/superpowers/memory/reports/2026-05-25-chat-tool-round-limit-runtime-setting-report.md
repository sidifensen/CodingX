## Summary
- Result: updated
- Source spec: none
- Source context: backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java, backend/src/main/java/com/codingx/chat/application/service/support/RuntimeSettingService.java, backend/src/main/resources/db/migration/V20260525_090000__add_chat_tool_runtime_settings.sql
- Source design: none
- Formal commits: 2501a467
- Created docs: 0
- Updated docs: 0
- Deferred docs: 0

## Durable updates made
- Established `chat.tool.max_rounds` as the system-config-backed limit for model-driven local tool call loops
- Raised the default tool-call round limit from 3 to 10 to reduce premature cutoff for tool-recall workflows
- Confirmed the chat tool-aware loop now reads a runtime setting instead of using a hard-coded constant

## Not promoted
- No existing canonical memory doc currently tracks chat tool-call runtime limits as a separate contract or lesson
- The unrelated working tree changes in the current workspace were intentionally left untouched

## Open gaps
- If the tool-call loop semantics evolve further, this should likely graduate into a dedicated chat/tool runtime contract doc
