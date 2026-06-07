# 聊天 MCP 工具运行边界

## 功能用途

聊天 MCP 运行时只保留当前真实可执行的 MCP 能力：内置天气工具 `weather_query` 和已发现成功的外部 MCP Server 工具。历史内置代码检索能力已下线，不再通过意图树、管理端 MCP 列表、运行时设置或模型工具清单暴露。

## 使用入口

用户在聊天页发送命中 MCP 意图的问题时，后端根据 `chat_intent_node.mcp_tool_id` 选择工具。管理员在 MCP 管理页只能维护仍存在的 MCP 配置；外部 MCP 需先发现工具并保持健康状态为可用，才会进入聊天工具清单。

## 核心流程

1. 管理端或迁移脚本初始化数据时，`init.sql` 与 `V20260607_222030__remove_code_search_mcp_and_intents.sql` 保证 `code` / `code-search` 意图、`code_search` MCP 和 `code_search.*` 运行时设置都不存在或被删除。
2. 用户消息进入 `ConversationIntentService.route` 后，只会从当前意图树读取叶子节点配置；若历史数据仍引用已删除的 `code_search`，路由不会命中可执行工具，后续由 MCP 可用性校验返回统一中文错误。
3. 聊天工具清单由 `ChatToolSpecService` 聚合本地工具、健康的外部 MCP 工具和内置 MCP 注册表；内置 MCP 注册表当前只注册真实执行器，未注册的 `code_search` 不会生成模型可见工具。
4. 模型发起 MCP 调用时，`ChatMcpExecutionService` 先识别 `mcp__{mcpCode}__{toolName}` 外部命名空间；普通内置 MCP 则交给 `ChatMcpToolRegistry`，未注册工具直接走不可用兜底，避免执行已下线能力。

## 关键文件

- `backend/src/main/resources/db/init.sql`：基线数据不再种子化代码检索意图、MCP 和运行时设置。
- `backend/src/main/resources/db/migration/V20260607_222030__remove_code_search_mcp_and_intents.sql`：清理已部署环境中的历史代码检索数据。
- `backend/src/main/java/com/codingx/mcp/application/service/ChatMcpExecutionService.java`：执行阶段区分外部 MCP 和内置 MCP 注册表。
- `backend/src/main/java/com/codingx/mcp/application/service/ChatMcpQueryService.java`：用户侧 MCP 可用性查询只返回仍存在且可用的 MCP。
- `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationIntentService.java`：按当前意图树配置路由 MCP 子问题。

## 测试与验证

- `mvn -Dtest=ChatIntentSeedScriptTest test`
- `mvn "-Dtest=ChatApplicationMcpFlowTest,ConversationIntentServiceTest,ChatMcpQueryServiceTest,AdminChatMcpControllerTest,ChatStreamControllerTest,ChatControllerConversationMutationTest,ChatIntentSeedScriptTest" test`
- `npm run test:run -- tests/pages/MCP.test.tsx tests/pages/Skills.test.tsx`
- 浏览器验证管理端意图树与 MCP 列表不再展示代码检索。
