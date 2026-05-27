# 联网搜索权威时效排序

## 功能用途

聊天命中联网搜索意图时，后端会把搜索 provider 返回的标题、链接、站点和摘要整理为模型证据。针对“最新”“当前”“版本”“发布”类公开信息问题，系统会优先让权威文档源、更新版本号和更新日期进入证据前列，避免旧页面或第三方未确认传言误导最终回答。

## 使用入口

- 用户聊天中提出搜索型问题，例如询问某产品、框架、服务或工具的最新版本与当前发布状态。
- `ConversationRewriteService` 只做术语归一化和上下文改写，不追加特定厂商、产品或域名锚点。
- `WebSearchExecutionService` 聚合搜索通道结果后，按后处理器顺序执行去重、权威最新排序、重排和 TopK 截断。

## 核心流程

1. `ConversationRewriteService` 对用户问题做术语归一化，保持原始搜索意图不被厂商特例污染。
2. `ConfigurableWebSearchChannel` 请求已配置的搜索 provider，返回 `SearchReferenceCandidate`。
3. `AuthoritativeLatestSearchPostProcessor` 仅在问题包含新鲜度意图时启用排序增强。
4. 排序增强基于通用站点形态识别权威来源，并从标题、URL、摘要提取语义版本号、日期和未确认语义做比较。
5. `ChatApplicationService` 将最终引用拼成联网检索证据，并提示模型优先使用官方产品文档、开发者文档、发布公告或变更日志。

## 关键文件

- `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationRewriteService.java`：执行术语归一化和 Prompt 改写，不维护特定厂商搜索锚点。
- `backend/src/main/java/com/codingx/chat/application/service/search/AuthoritativeLatestSearchPostProcessor.java`：对权威来源、版本号、日期和未确认语义做搜索结果后处理排序。
- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：生成联网检索证据与回答约束。
- `backend/src/test/java/com/codingx/chat/application/service/WebSearchExecutionServiceTest.java`：覆盖权威文档源的新版本优先于旧版本和第三方未确认版本。
- `backend/src/test/java/com/codingx/chat/application/service/ConversationRewriteServiceTest.java`：覆盖改写服务不会追加特定厂商或域名锚点。

## 边界条件

- 当前实现不抓取网页正文，只基于搜索 API 返回的结构化摘要和链接排序。
- 权威时效排序只在问题包含“最新”“当前”“版本”“发布”等新鲜度意图时启用，不影响普通技术搜索或百科查询。
- 第三方来源声称存在更新版本但权威证据未确认时，模型证据约束要求不得写成已确认结论。

## 验证方式

```bash
cd backend
mvn -Dtest=WebSearchExecutionServiceTest,ConversationRewriteServiceTest test
```
