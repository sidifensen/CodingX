# 天气 MCP 城市澄清

## 功能用途

天气 MCP 查询必须包含城市。用户只问“天气怎么样”“那个天气怎么样”时，系统在执行 MCP 前先要求补充城市，避免工具错误结果进入模型上下文后产生空泛回答。

## 使用入口

用户在聊天页发送天气类问题，并已启用 `weather_query` MCP。

## 核心流程

1. `ConversationIntentService` 完成意图识别，最高候选为 `weather-data`。
2. 如果该节点绑定 `weather_query`，路由层调用 `WeatherQuestionParser.hasCity` 检查城市槽位。
3. 缺少城市时返回 `CLARIFY`，回复“请明确你想查询哪个城市的天气，例如：上海今天天气怎么样。”。
4. 已包含城市时继续返回 `MCP`，由 `WeatherMcpToolExecutor` 复用同一解析器提取城市、查询类型和预报天数。
5. MCP 仍保留缺城市兜底，防止绕过路由保护的直接工具调用。

## 关键文件

- `backend/src/main/java/com/codingx/chat/application/service/conversation/ConversationIntentService.java`：天气 MCP 缺城市时短路澄清。
- `backend/src/main/java/com/codingx/mcp/application/executor/WeatherQuestionParser.java`：天气城市、查询类型和天数解析。
- `backend/src/main/java/com/codingx/mcp/application/executor/WeatherMcpToolExecutor.java`：执行天气 MCP，并复用天气参数解析器。

## 测试与验证

- `mvn "-Dtest=ConversationIntentServiceTest,WeatherQuestionParserTest,WeatherMcpToolExecutorTest" test`
- `mvn compile`
- `mvn test`
