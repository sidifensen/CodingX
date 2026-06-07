# 聊天拆分问题逐题意图路由

## 功能用途

聊天主链路支持把一次用户输入拆分成多个子问题后分别识别意图，避免整体问题命中搜索后，天气、外部 MCP 等工具子问题被错误送入网页搜索。

## 使用入口

用户在聊天页发送包含多个诉求的消息，例如“搜一下现在 AI 哪个最强，然后今天北京的天气怎么样，然后量子力学是什么”。后端会先改写并拆分，再对每个子问题独立路由。

## 核心流程

1. `ConversationRewriteService.rewriteResult` 返回改写问题和 `subQuestions`。
2. `ChatApplicationService` 使用 `subQuestions` 逐个调用 `ConversationIntentService.route`；未拆分时回退到改写问题。
3. 命中 `MCP` 的子问题先按子问题原文执行对应 MCP，并把工具结果追加为系统证据。
4. 命中 `SEARCH` 的子问题合并进入并行搜索队列，天气等 MCP 子问题不会进入网页搜索。
5. 最终模型同时接收会话历史、MCP 工具证据和搜索引用，生成综合回答。

## 关键文件

- `backend/src/main/java/com/codingx/chat/application/service/chat/ChatApplicationService.java`：编排拆分问题逐题路由、MCP 执行、搜索执行和最终模型调用。
- `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationSearchFlowTest.java`：覆盖混合搜索、天气 MCP 和普通知识子问题的回归场景。
- `backend/src/test/java/com/codingx/chat/application/service/ChatApplicationMcpFlowTest.java`：覆盖单问题 MCP 调用兼容性。

## 关键逻辑

逐题路由遵循“拆分优先于意图识别”的约束，和 ragent 的 `rewriteWithSplit -> resolve(subQuestions) -> retrieve` 顺序一致。澄清、MCP 未启用和 MCP 配置缺失仍保持短路语义，避免部分工具或搜索已经执行后才提示用户配置问题。

主意图用于运行结果落库和搜索证据系统提示；只要本轮存在搜索子问题，就优先选择搜索意图，确保模型回答时带上联网证据约束。

意图解析遵循 ragent 风格的配置驱动分类：仅把叶子节点的完整路径、描述、类型和示例交给模型评分，不在 Java 中维护“天气”“最近”“最新”等业务关键词表。天气 MCP 能命中 `weather-data`，依赖的是该节点自身的描述、示例和 `mcpToolId` 配置；模型不可用时，本地兜底也只读取这些配置文本做轻量匹配。

## 测试与验证

- `mvn -Dtest=ChatApplicationSearchFlowTest#sendMessageRoutesEachSplitQuestionBeforeExecutingSearchAndMcp test`
- `mvn "-Dtest=ChatApplicationSearchFlowTest,ChatApplicationMcpFlowTest" test`
- `mvn "-Dtest=ConversationIntentResolverTest" test`
- 后端提交前继续执行 `mvn compile` 和 `mvn test`。
