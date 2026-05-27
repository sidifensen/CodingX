# Chat Split Intent Routing Acceptance Criteria

## 验收标准

- 给定改写结果包含多个子问题，后端必须逐个调用 `ConversationIntentService.route(subQuestion, mcpEnabled)`。
- 当子问题分别命中搜索和天气 MCP 时，搜索服务只接收搜索子问题，天气子问题只调用 `weather_query`。
- MCP 工具结果必须以系统证据追加到模型历史中，最终回答仍由模型流式生成。
- 单问题搜索和单问题 MCP 的既有测试继续通过。
- 聊天功能文档必须记录“先拆分再逐题路由”的当前实现。

## 验证方式

- 运行混合问题回归测试，确认修复前失败、修复后通过。
- 运行 `ChatApplicationSearchFlowTest` 和 `ChatApplicationMcpFlowTest`。
- 后端执行 `mvn compile` 与 `mvn test`。
