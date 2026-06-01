# Acceptance Criteria: Web Search Provider Fallback

**Spec:** `docs/superpowers/specs/2026-05-30-web-search-provider-fallback-design.md`
**Date:** 2026-05-30
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 系统配置缺失时搜索 provider 默认顺序为 Tavily、SerpApi、Exa、DuckDuckGo HTML、Bing HTML。 | Logic | `setting` 缓存中不存在 `web_search.provider_order`。 | `RuntimeSettingService.webSearchProviderOrder()` 返回 `["tavily","serpapi","exa","duckduckgo_html","bing_html"]`。 |
| AC-002 | 管理端可通过系统配置自由调整搜索 provider 顺序。 | Logic | `setting` 中配置 `web_search.provider_order=bing_html,tavily,exa`。 | 搜索通道按 `bing_html -> tavily -> exa` 尝试，不尝试未列出的 provider。 |
| AC-003 | API provider 密钥配置必须通过敏感配置读取，不允许出现在仓库 SQL 中。 | Logic | `web_search.providers.serpapi.api_key` 使用 `secret=true` 和 `encrypted_value`。 | `RuntimeSettingService` 返回解密后的 key；迁移脚本中该 key 的默认 `setting_value` 为空。 |
| AC-004 | 搜索链路在首个 provider 失败时自动切换到下一个 provider。 | Logic | Tavily 本地测试服务返回 500，SerpApi 本地测试服务返回 1 条有效结果。 | 搜索结果来自 SerpApi，日志/可观察状态显示先尝试 Tavily 再尝试 SerpApi。 |
| AC-005 | 搜索 provider 连续失败达到阈值后进入熔断并跳过后续请求。 | Logic | `web_search.failure_threshold=1`，Tavily 已因失败进入 OPEN。 | 下一次搜索不请求 Tavily，直接尝试后续 provider。 |
| AC-006 | 搜索 provider 熔断冷却结束后只允许一个半开探测请求，成功后恢复。 | Logic | Tavily 已 OPEN 且冷却时间已过。 | 第一次 `allowCall` 返回 true，第二次并发前返回 false；成功标记后再次返回 true。 |
| AC-007 | Tavily、SerpApi、Exa 响应能解析为标准搜索来源候选。 | Logic | 本地 HTTP 测试服务分别返回三种 provider 响应 fixture。 | 候选结果包含标题、URL、站点名、摘要和分数，缺失标题或 URL 的条目被过滤。 |
| AC-008 | DuckDuckGo HTML 和 Bing HTML 可作为无密钥兜底 provider。 | Logic | provider 顺序只包含 HTML provider，本地 HTTP 测试服务返回搜索结果 HTML。 | 搜索通道无需 API key 即启用，并返回标准来源候选。 |
| AC-009 | 旧 `web_search.provider/base_url/api_key` 配置已下线，不再展示也不参与运行时。 | Data | `setting` 表中仍存在旧键历史行。 | 迁移后旧键 `deleted=1`，管理端系统配置列表不返回这些键，搜索链路只读取 provider 级配置。 |
| AC-010 | 搜索日志不能泄露 API key。 | Logic | provider 级 key 配置为测试密钥。 | 搜索相关日志只包含 provider 编码、结果数和失败类型，不包含 key 明文。 |
