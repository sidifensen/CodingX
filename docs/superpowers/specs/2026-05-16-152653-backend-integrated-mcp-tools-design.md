# CodingX 后端内聚 MCP 工具设计

## 背景与目标

当前 `CodingX` 聊天链路已经具备 `ChatMcpToolExecutor` 抽象，但线上只接入了 `MockSalesMcpToolExecutor`，`ticket_query` 和 `weather_query` 在意图树中已有配置却没有可执行实现，导致 MCP 路由能力不完整。

本次目标是在**不新增独立服务**的前提下，将 `ragent` 的 MCP 工具能力按“同进程执行器”方式落入 `backend`，让应用内自用场景先可用、可测、可维护。

## 范围

- 新增三个后端内聚 MCP 工具执行器：
  - `sales_query`
  - `ticket_query`
  - `weather_query`
- 保持现有聊天主链路 (`ChatMcpExecutionService` + `ChatMcpToolRegistry`) 不变，仅通过新增 `ChatMcpToolExecutor` 实现接入。
- 保留 `MockSalesMcpToolExecutor` 代码用于回归/应急，但默认禁用，避免与真实 `sales_query` 冲突。
- 新增根目录 `.mcp.json`，同步 `postgres` / `redis` / `chrome-devtools` MCP 客户端配置，便于本地工具链一致。

不在本次范围：
- 不新增独立 `mcp-server` 进程与部署链路。
- 不重构前端页面与交互。
- 不改动数据库结构（当前 `chat_intent_node` 的 `mcp_tool_id` 已具备）。

## 方案决策

### 方案 A（采用）：后端内聚执行器

直接在 `backend` 中实现 `SalesMcpToolExecutor`、`TicketMcpToolExecutor`、`WeatherMcpToolExecutor` 三个 `@Component`，由既有注册表自动发现。

优点：
- 无额外部署与运维成本；
- 与现有业务链路耦合最小（新增类即可生效）；
- 支持以后按相同接口平滑迁移到远程 MCP 调用实现。

### 方案 B（未采用）：新增独立 mcp-server

复制 `ragent/mcp-server` 独立起服务，通过 HTTP/MCP 协议由 `backend` 调用。

不采用原因：
- 当前主要是应用内自用，独立服务收益小于成本；
- 需要额外端口、启动、监控、超时/重试治理，超出本次性价比。

## 关键设计

### 1. 工具执行器契约保持不变

- 继续使用：
  - `ChatMcpToolExecutor#toolId()`
  - `ChatMcpToolExecutor#execute(String question)`
- `ChatMcpExecutionService` 与 `ChatApplicationService` 无需改造。

### 2. 参数解析策略

三类工具均采用“轻量启发式解析”：
- `sales_query`：识别地区、周期、产品、查询类型（汇总/排名/趋势/明细）和前 N。
- `ticket_query`：识别地区、状态、优先级、产品、客户、查询类型（汇总/列表/统计）。
- `weather_query`：识别城市、查询类型（当前/预报）、预报天数。

解析失败时统一降级到可解释的默认行为，不抛出技术异常给用户。

### 3. 数据生成与结果稳定性

- `sales_query`、`ticket_query` 继续使用固定随机种子的模拟数据模式，保证同日结果稳定，便于测试验证。
- `weather_query` 按“城市 + 日期”生成稳定天气，确保可复现。

### 4. Mock 冲突处理

`MockSalesMcpToolExecutor` 加条件开关：
- `@ConditionalOnProperty(prefix = "app.chat.mcp.mock-sales", name = "enabled", havingValue = "true")`
- 默认不开启，避免与真实 `sales_query` Bean 冲突。

## 测试设计

遵循 TDD：
1. 先补失败测试（新增执行器测试 + 调整分发测试）。
2. 再实现生产代码使测试转绿。

新增测试覆盖：
- `SalesMcpToolExecutorTest`
- `TicketMcpToolExecutorTest`
- `WeatherMcpToolExecutorTest`
- `ChatMcpExecutionServiceTest`（改为验证分发到真实执行器）

## 风险与缓解

- 风险：自然语言解析误判查询类型。
  - 缓解：关键词优先级 + 默认汇总兜底，避免空结果或异常。
- 风险：同 `toolId` 多执行器冲突。
  - 缓解：默认关闭 mock Bean。
- 风险：后续真实外部数据接入改造成本。
  - 缓解：保持 `ChatMcpToolExecutor` 接口稳定，后续可新增远程实现替换。

## 验收标准（摘要）

- 意图树中 `sales_query` / `ticket_query` / `weather_query` 均可在后端执行并返回中文业务文本。
- 聊天 MCP 分发链路无需改主流程即可正常路由。
- 测试命令通过：
  - 定向执行器测试与分发测试通过。
  - 后端全量 `mvn compile`、`mvn test` 通过。
