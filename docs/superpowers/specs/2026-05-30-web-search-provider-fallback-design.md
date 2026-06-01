# Web Search Provider Fallback Design

## 背景

当前真实联网搜索由 `ConfigurableWebSearchChannel` 通过 `web_search.provider_order` 和 `web_search.providers.<provider>.*` 读取 provider 链路配置。该模型可以按顺序自动 fallback、优先国内可达源、使用 HTML 免费兜底并对单个 provider 做故障熔断。

## 目标

- 默认搜索顺序调整为 `tavily,serpapi,exa,duckduckgo_html,bing_html`。
- 搜索 provider 顺序必须通过系统配置表自由调整，允许删减 provider。
- Tavily、SerpApi、Exa 走正式 API；DuckDuckGo HTML、Bing HTML 作为无密钥网页解析兜底。
- 搜索链路日志必须说明本次尝试了哪些 provider、最终使用哪个 provider、哪些 provider 失败或被熔断跳过。
- 搜索 provider 故障切换要具备与模型故障切换一致的三态熔断语义：连续失败后熔断、冷却后半开探测、成功后恢复。
- 用户提供的 SerpApi / Exa key 不写入仓库；系统配置只提供敏感配置槽位，由管理端或本地数据库加密写入。

## 范围

### 本次包含

- 后端搜索配置读取扩展。
- 搜索 provider 顺序解析、provider 级配置、旧单源配置下线。
- Tavily、SerpApi、Exa、DuckDuckGo HTML、Bing HTML 请求与响应解析。
- 搜索 provider 级健康熔断。
- 数据库迁移脚本与 `init.sql` 初始化配置。
- 功能文档和后端单元测试。

### 本次不包含

- 前端系统配置页面改版。
- 手动执行真实外网搜索冒烟，避免消耗用户 key。
- 将用户明文 key 写入 Git 管理的 SQL 文件。

## 架构设计

`ConfigurableWebSearchChannel` 从单 provider 适配器演进为“provider fallback 调度器”。它仍然是唯一 `SearchChannel` Bean，上层 `WebSearchExecutionService` 的搜索聚合与后处理链不需要调整。

新增搜索 provider 配置模型：

- `web_search.provider_order`：逗号分隔 provider 顺序，默认 `tavily,serpapi,exa,duckduckgo_html,bing_html`。
- `web_search.failure_threshold`：连续失败熔断阈值，默认复用模型路由阈值 `2`。
- `web_search.open_duration_ms`：熔断打开时长，默认复用模型路由 `30000`。
- `web_search.providers.<provider>.base_url`：provider endpoint。
- `web_search.providers.<provider>.api_key`：provider 密钥；Tavily、SerpApi、Exa 为敏感配置，HTML provider 为空。

旧配置下线：

- 旧 `web_search.provider/base_url/api_key` 从默认系统配置中移除，并由迁移脚本标记为已删除。
- 搜索链路不再读取旧单源配置；provider 级配置缺失时跳过该 provider 并尝试后续 provider。

## Provider 协议

- Tavily：`POST /search`，`Authorization: Bearer <api_key>`，响应读取 `results[].title/url/content/score`。
- SerpApi：`GET /search.json` 或配置 base URL，查询参数包含 `engine=google`、`q`、`api_key`、`hl`、`gl`、`num`，响应读取 `organic_results[].title/link/snippet`。
- Exa：`POST /search`，`x-api-key: <api_key>`，响应读取 `results[].title/url/text/score`。
- Bing HTML：`GET /search?q=...`，解析结果列表中的标题、链接和摘要。
- DuckDuckGo HTML：`GET /html/?q=...`，解析结果列表中的标题、链接和摘要。

HTML 解析必须使用 Hutool HTML 工具或结构化 DOM 方式，不能用脆弱正则直接截取整页内容。解析失败、无有效标题或 URL 时返回空结果并进入下一个 provider。

## 故障切换

新增 `SearchProviderHealthRegistry`，保留与 `AiProviderHealthRegistry` 相同的三态熔断行为，但独立状态存储，避免搜索失败影响模型路由。

一次搜索请求流程：

1. 读取 `provider_order` 并过滤空白、重复、不支持 provider。
2. 对每个 provider 先检查健康注册表，熔断中则跳过并记录日志。
3. 配置不完整的 provider 直接跳过，不计入失败。
4. 请求 provider，若返回有效结果则标记成功、记录“使用 provider”，停止 fallback。
5. 请求异常或结果为空时标记失败，继续下一个 provider。
6. 所有 provider 均不可用时抛出统一中文搜索不可用异常。

## 日志与安全

- 日志只打印 provider 编码、请求问题截断文本、结果数量、失败原因类型，不打印 API key、密文或完整响应。
- SerpApi / Exa 用户 key 不出现在迁移脚本、初始化脚本、功能文档、测试 fixture 或日志中。
- 管理端保存敏感配置仍由 `AdminChatSettingsService` 统一加密。

## 测试

- RuntimeSettingService：覆盖 provider 顺序、provider 级配置、敏感 key 解密、故障阈值和冷却时间读取。
- SearchProviderHealthRegistry：覆盖 CLOSED / OPEN / HALF_OPEN 状态机。
- ConfigurableWebSearchChannel：覆盖顺序 fallback、熔断跳过、Tavily、SerpApi、Exa、DuckDuckGo HTML、Bing HTML 解析、旧配置下线。
- 后端编译与定向测试：`mvn -Dtest=RuntimeSettingServiceTest,SearchProviderHealthRegistryTest,ConfigurableWebSearchChannelTest test`。

## 风险

- HTML 搜索结果页结构可能变化，需以失败安全方式降级到后续 provider。
- 真实 provider key 未配置时 API provider 会被跳过，只有 HTML provider 参与兜底。
- 国内网络可达性因环境变化，默认顺序只是初始策略，后续应通过系统配置调整。
