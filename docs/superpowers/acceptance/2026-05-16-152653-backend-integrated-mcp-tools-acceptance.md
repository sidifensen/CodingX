# 后端内聚 MCP 工具验收标准

## 1. 工具注册与分发

1. 当 Spring 容器启动后，`ChatMcpToolRegistry` 能发现并注册以下 `toolId` 对应执行器：
   - `sales_query`
   - `ticket_query`
   - `weather_query`
2. 当调用 `ChatMcpExecutionService.execute("sales_query", "...")` 时，返回结果 `toolId` 必须为 `sales_query`，且内容包含销售汇总标题。

## 2. 销售工具行为

1. 输入含“本月华东销售总额”类问题时，返回内容包含：
   - `销售数据汇总`
   - `本月`
   - `华东`
2. 输入含“排名/前五”类问题时，返回内容包含：
   - `销售排名`
   - `第1名`

## 3. 工单工具行为

1. 默认查询（如“华东区待处理工单有多少”）返回内容包含 `客户工单汇总概览`。
2. 列表查询（如“列出紧急工单列表”）返回内容包含 `工单列表`。

## 4. 天气工具行为

1. 当前天气查询（如“北京今天天气怎么样”）返回内容包含 `今日天气`。
2. 预报查询（如“上海未来三天天气预报”）返回内容包含 `未来3天天气预报`。

## 5. Mock 冲突控制

1. 在未配置 `app.chat.mcp.mock-sales.enabled=true` 时，`MockSalesMcpToolExecutor` 不应作为有效 `sales_query` 执行器参与注册。
2. 系统应默认使用真实 `SalesMcpToolExecutor`。

## 6. 质量门禁

1. 以下定向测试全部通过：
   - `SalesMcpToolExecutorTest`
   - `TicketMcpToolExecutorTest`
   - `WeatherMcpToolExecutorTest`
   - `ChatMcpExecutionServiceTest`
2. 后端编译与全量测试通过：
   - `mvn compile`
   - `mvn test`
