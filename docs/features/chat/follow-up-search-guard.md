# 聊天代码产物续写搜索保护

## 功能用途

代码生成会话中，用户常用“丰富一下”“优化一下”“继续”等短句要求助手基于上一轮文件产物继续编辑。该保护避免这类上下文延续请求被通用意图分类误判为联网搜索，从而错误调用网页搜索。

## 使用入口

用户在聊天页或桌面端聊天中先完成 HTML、脚本、补丁或本地文件生成，再发送短编辑指令。后端仍走统一聊天入口 `ChatApplicationService.sendMessage`，不需要前端新增参数。

## 核心流程

1. 用户发送短编辑指令后，后端先保存本轮用户消息，并将历史消息交给 `ConversationRewriteService` 改写。改写结果仍保留“丰富一下”等短句，供后续模型结合历史理解。
2. `ConversationIntentService` 对改写后的子问题做正常意图分流；如果分类结果是 `SEARCH`，`ChatApplicationService` 会进入搜索决策保护检查。
3. 保护检查同时判断当前输入是否为短编辑/续写指令、是否没有显式搜索或时效词、历史中是否存在 HTML、文件写入、本地工具或补丁产物信号。三个条件都满足时，后端把该子问题从 `SEARCH` 降级为 `DIRECT`。
4. 降级后 `searchQuestions(...)` 不会收集到搜索子问题，因此不会创建搜索步骤、调用搜索 provider、写引用来源或生成搜索整理文档。
5. 模型调用仍会收到本轮短句和历史消息，继续基于上一轮代码产物生成回答；如果用户明确输入“搜一下”“查询最新版本”等检索词，则保护不会触发，原联网搜索链路保持不变。

## 关键文件

- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：在搜索执行前对代码产物续写短句做决策降级。
- `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationSearchFlowTest.java`：覆盖短句误判为 `SEARCH` 时不调用网页搜索的回归测试。
- `docs/features/chat/follow-up-search-guard.md`：记录当前保护规则和验证方式。

## 边界条件

- 只有历史中存在代码、HTML、文件写入、补丁或本地工具产物信号时才触发，普通新会话短句不会被特殊处理。
- 当前输入包含“搜索”“查询”“最新”“今天”“联网”“网页”“新闻”“版本”等显式检索词时，不触发保护。
- 保护只改变本轮后端搜索执行决策，不修改意图树配置，也不关闭系统联网搜索总开关。
- 混合问题中只有命中保护的子问题会降级；其他搜索或 MCP 子问题继续按既有规则执行。

## 测试与验证

```bash
cd backend
mvn -Dtest=ChatApplicationSearchFlowTest#sendMessageDoesNotInvokeSearchForCodeArtifactFollowUpShortEdit test
mvn -Dtest=ChatApplicationSearchFlowTest test
mvn compile
mvn test
```
