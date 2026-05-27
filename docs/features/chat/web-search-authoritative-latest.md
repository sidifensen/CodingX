# 联网搜索权威最新排序

## 功能用途

聊天命中联网搜索意图时，后端会把搜索 provider 返回的标题、链接、站点和摘要整理为模型证据。针对“最新”“当前”类公开模型或版本问题，系统会优先让官方来源和更新版本号进入证据前列，避免旧官方页或第三方传言误导最终回答。

## 使用入口

- 用户聊天中提出搜索型问题，例如“gpt最新模型是什么”。
- `ConversationRewriteService` 会先做术语归一化，并对 GPT/OpenAI 最新模型问题补充 `OpenAI 官方文档 latest model developers.openai.com` 搜索锚点。
- `WebSearchExecutionService` 聚合搜索通道结果后，按后处理器顺序执行去重、权威最新排序、重排和 TopK 截断。

## 核心流程

1. `ConversationRewriteService` 识别 GPT/OpenAI 最新模型查询并追加官方文档限定词。
2. `ConfigurableWebSearchChannel` 请求已配置的搜索 provider，返回 `SearchReferenceCandidate`。
3. `AuthoritativeLatestSearchPostProcessor` 仅在“最新/当前 + 模型”类问题上启用排序增强。
4. 排序增强优先识别 `developers.openai.com`、`platform.openai.com`、`openai.com` 等官方域名，并从标题、URL、摘要提取 GPT 版本号做数值比较。
5. `ChatApplicationService` 将最终引用拼成联网检索证据，并提示模型官方来源优先、版本号冲突必须比较新旧。

## 关键文件

- `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationRewriteService.java`：补充 GPT/OpenAI 最新模型官方文档查询锚点。
- `backend/src/main/java/com/codingx/chat/application/service/search/AuthoritativeLatestSearchPostProcessor.java`：对权威来源和版本号做搜索结果后处理排序。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：生成联网检索证据与回答约束。
- `backend/src/test/java/com/codingx/chat/application/service/WebSearchExecutionServiceTest.java`：覆盖 GPT-5.5 官方结果优先于 GPT-5.4 旧结果。
- `backend/src/test/java/com/codingx/chat/application/service/ConversationRewriteServiceTest.java`：覆盖 GPT 最新模型查询补充官方文档锚点。

## 边界条件

- 当前实现不抓取网页正文，只基于搜索 API 返回的结构化摘要和链接排序。
- 官方来源权重只在最新/当前模型类问题中启用，不影响普通技术搜索或百科查询。
- 第三方来源声称存在更新版本但官方证据未确认时，模型证据约束要求不得写成已确认结论。

## 验证方式

```bash
cd backend
mvn -Dtest=WebSearchExecutionServiceTest,ConversationRewriteServiceTest test
```
