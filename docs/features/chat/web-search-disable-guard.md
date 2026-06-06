# 系统联网搜索禁用守卫

## 功能用途

聊天运行时支持按 `web_search.enabled` 控制系统内置联网搜索。关闭后，搜索型意图只作为普通模型输入处理，不再创建搜索过程卡片、调用外部搜索 provider、写入搜索引用或生成搜索整理文档。用户显式选择 `web-access` 技能时不会禁用系统搜索；搜索类问题仍先走 CodingX 搜索证据链，再把搜索证据和技能上下文一起交给模型总结。

## 使用入口

- 管理端或运行时配置将 `web_search.enabled` 设置为 `false`。
- 用户在聊天输入中选择或输入 `@web-access`，例如 `@web-access 搜一下今天的新闻`。该场景用于验证技能上下文不会阻断搜索证据链。

## 核心流程

1. `ChatCapabilityMentionSupport` 合并前端显式技能和正文前缀里的技能编码。
2. `ConversationIntentService` 仍按自然语言完成意图识别。
3. `ChatApplicationService` 在搜索执行前只检查系统搜索开关；选择 `web-access` 不再作为禁用理由。
4. 命中 `web_search.enabled=false` 时，`SEARCH` 子问题降级为 `DIRECT`，后续不进入 `executeSearchQuestions(...)`。
5. 系统搜索开启且用户选择 `web-access` 时，`executeSearchQuestions(...)` 会继续调用搜索 provider、收集引用并生成搜索整理文档；模型系统消息同时包含 `# 联网检索证据` 与技能上下文。由于搜索证据已经由系统链路拿到，后续模型调用会隐藏本地工具列表，只做普通流式总结，避免 `web-access` 再驱动 Bash/CDP 工具循环并停在“正在搜索”“正在执行”过程文案。

## 关键文件

- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：集中执行自动搜索守卫和搜索决策降级。
- `backend/src/main/java/com/codingx/chat/application/service/support/RuntimeSettingService.java`：读取 `web_search.enabled` 运行时开关。
- `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationSearchFlowTest.java`：覆盖搜索关闭和 `web-access` 技能选择后仍走搜索证据链的回归场景。

## 边界条件

- 本守卫只禁用系统内置搜索，不新增 `web-access` 的 Java/CDP 执行器。
- 系统搜索开启时，无论是否选择 `web-access`，原有搜索、引用收集和文档产物链路都保持可用。
- 选择 `web-access` 后，技能说明只作为模型联网访问约束和短句解释上下文，不能让搜索类请求退化为普通直答。
- 系统搜索已返回引用时，本轮模型不再暴露本地工具 schema；URL 读取、当前页面操作等非搜索型 `web-access` 任务仍按本地工具循环执行。
- 混合问题中的 MCP 子问题不受搜索守卫影响。

## 验证方式

```bash
cd backend
mvn -DskipTests compile
mvn -Dtest=ChatApplicationSearchFlowTest#sendMessageSkipsSearchFlowWhenWebSearchDisabled+sendMessageWithWebAccessSkillStillUsesSystemSearchEvidence test
```
