# 联网搜索提供方顺序与故障切换

## 功能用途

聊天联网搜索支持按系统配置表维护 provider 尝试顺序，并在单个 provider 配置缺失、请求失败、无有效结果或进入熔断时自动切换到下一个 provider。默认顺序面向国内用户调整为 `tavily,serpapi,exa,duckduckgo_html,bing_html`。

## 使用入口

- 管理端系统配置：`web_search.provider_order`
- Provider 级配置：`web_search.providers.<provider>.base_url`、`web_search.providers.<provider>.api_key`
- 熔断配置：`web_search.failure_threshold`、`web_search.open_duration_ms`
- 后端搜索入口：`ConfigurableWebSearchChannel`

## 核心流程

1. `RuntimeSettingService` 从 `setting` 表读取 provider 顺序和 provider 级配置。
2. `ConfigurableWebSearchChannel` 按顺序过滤重复、未知、配置缺失或熔断中的 provider。
3. Tavily、SerpApi、Exa 走正式 API；DuckDuckGo HTML、Bing HTML 解析搜索结果页作为无密钥兜底。
4. 单个 provider 失败或无有效结果时记录失败并进入下一个 provider；成功后记录使用的 provider 并停止 fallback。
5. 连续失败达到阈值后，该 provider 进入熔断；冷却结束后只允许一次半开探测，成功后恢复。

## 关键文件

- `backend/src/main/java/com/codingx/chat/infrastructure/search/ConfigurableWebSearchChannel.java`：搜索 provider fallback 调度与协议适配。
- `backend/src/main/java/com/codingx/chat/infrastructure/search/SearchProviderHealthRegistry.java`：搜索 provider 三态熔断状态机。
- `backend/src/main/java/com/codingx/chat/application/service/support/RuntimeSettingService.java`：搜索顺序、provider 配置和熔断参数读取。
- `backend/src/main/resources/db/migration/V20260530_210000__add_web_search_provider_fallback_settings.sql`：系统配置表种子。
- `backend/src/main/resources/db/init.sql`：新初始化环境的系统配置表默认值。

## 关键数据结构

- `web_search.provider_order`：逗号分隔的 provider 顺序，可自由调整或删减。
- `web_search.providers.tavily.api_key`、`web_search.providers.serpapi.api_key`、`web_search.providers.exa.api_key`：敏感配置槽位，默认不包含真实密钥。
- `web_search.failure_threshold`：连续失败熔断阈值。
- `web_search.open_duration_ms`：熔断打开时长毫秒。

## 约束与边界

- 用户真实搜索密钥不能写入迁移脚本、`init.sql`、测试 fixture 或日志。
- 日志只允许打印 provider 编码、尝试顺序、结果数和失败类型，不打印 API Key 或 provider 原始响应全文。
- `web_search.provider/base_url/api_key` 已下线，不再展示也不再参与运行时读取；搜索顺序和 endpoint 只由 `web_search.provider_order` 与 provider 级配置控制。
- HTML provider 依赖外部页面结构，解析失败时必须安全降级到下一个 provider。

## 测试与验证

```bash
cd backend
mvn -Dtest=RuntimeSettingServiceTest,SearchProviderHealthRegistryTest,ConfigurableWebSearchChannelTest test
mvn compile
mvn test
```
