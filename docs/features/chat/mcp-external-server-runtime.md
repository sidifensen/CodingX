# 外部 MCP Server 运行时

## 功能用途

聊天运行时支持把外部 MCP Server 发现出的工具暴露给模型，并通过 `mcp__{mcpCode}__{toolName}` 命名空间执行远程 JSON-RPC 调用。当前版本支持 `http` / `sse` 远程端点；`stdio` 配置会返回中文不支持提示，避免后端误启动不可控本地进程。

## 使用入口

管理端或数据库维护外部 MCP 配置后，调用运行时发现工具。发现成功会把工具 schema 快照写入 `mcp.tool_schema_json`，聊天工具清单会自动合并健康状态为 `AVAILABLE` 的外部 MCP 工具。

## 核心流程

1. `McpServerRuntimeService.discoverTools(mcpCode)` 读取 `mcp` 表配置，校验 `sourceType=external`、传输类型和远程端点后，向端点 POST JSON-RPC `tools/list`。
2. 远程返回的 `result.tools[]` 会转换成 `ChatToolSpec`，模型可见名称和 canonical 编码都使用 `mcp__github__search` 这类命名空间格式。
3. 发现成功后，服务写回 `toolSchemaJson`、`healthStatus=AVAILABLE`、`lastConnectedAt` 并清空错误；失败时写回 `healthStatus=ERROR` 和中文错误摘要。
4. `ChatToolSpecService` 每轮生成模型工具清单时，会追加 `McpServerRuntimeService.listDiscoveredToolSpecs()` 返回的已发现外部 MCP 工具，不会在聊天请求内主动联网发现。
5. 模型调用 `mcp__{mcpCode}__{toolName}` 时，`ChatMcpExecutionService` 将其路由到外部 MCP 运行时；普通内置 MCP 仍走原有 `ChatMcpToolRegistry`。
6. 远程执行使用 JSON-RPC `tools/call`，参数为 `{name, arguments}`；返回的 `content[].text` 会合并成工具正文，`structuredContent`、`mcpCode` 和 `toolName` 放入 metadata。

## 关键文件

- `backend/src/main/java/com/codingx/mcp/application/service/McpToolNameSupport.java`：构造和解析 `mcp__server__tool` 工具名。
- `backend/src/main/java/com/codingx/mcp/application/service/McpServerRuntimeService.java`：发现外部 MCP 工具、执行远程 JSON-RPC 调用、维护健康状态。
- `backend/src/main/java/com/codingx/mcp/application/service/ChatMcpExecutionService.java`：区分命名空间外部 MCP 与内置 MCP 注册表。
- `backend/src/main/java/com/codingx/mcp/application/service/ChatMcpQueryService.java`：用户侧 MCP 可用性判断，外部 MCP 依赖健康状态和 schema 快照。
- `backend/src/main/java/com/codingx/tool/application/service/ChatToolSpecService.java`：把已发现外部 MCP 工具并入模型可见工具清单。
- `backend/src/main/resources/db/migration/V20260606_170900__add_external_mcp_runtime_fields.sql`：为 `mcp` 表补充外部运行时字段。
- `backend/src/main/resources/db/schema.sql`：同步基线表结构。

## 测试与验证

- `mvn -Dtest=McpToolNameSupportTest,McpServerRuntimeServiceTest,ChatMcpExecutionServiceTest,ChatMcpQueryServiceTest,ChatToolSpecServiceTest test`
