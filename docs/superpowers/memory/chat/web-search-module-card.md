---
type: module_card
title: chat-web-search-runtime
summary: 记录聊天联网搜索运行时的通道、系统配置与后处理边界
tags:
  - chat
  - search
owned_paths:
  - backend/src/main/java/com/codingx/chat/application/service/search
  - backend/src/main/java/com/codingx/chat/infrastructure/search
  - backend/src/main/java/com/codingx/chat/application/service/support/RuntimeSettingService.java
  - backend/src/main/resources/db/migration/*web_search*
related_docs:
  - docs/superpowers/memory/chat/web-search-runtime-contract.md
  - docs/superpowers/specs/2026-05-20-233100-real-web-search-channel-design.md
entrypoints:
  - backend/src/main/java/com/codingx/chat/application/service/search/WebSearchExecutionService.java
  - backend/src/main/java/com/codingx/chat/infrastructure/search/ConfigurableWebSearchChannel.java
  - backend/src/main/java/com/codingx/chat/application/service/support/RuntimeSettingService.java
last_verified_commit: a7626895
status: active
---

# Chat Web Search Runtime Module Card

## Responsibilities

- `WebSearchExecutionService` 负责筛选启用的 `SearchChannel`，按 `getPriority()` 升序执行搜索通道，聚合结果后依次运行搜索后处理器。
- `ConfigurableWebSearchChannel` 是当前真实联网搜索入口，通过 `RuntimeSettingService` 读取 `web_search.*` 系统配置，构造 provider 请求并映射为 `SearchReferenceCandidate`。
- `SearchProviderHealthRegistry` 维护搜索 provider 独立三态熔断状态，语义与模型路由健康注册表一致，但不会影响 AI provider 状态。
- `RuntimeSettingService` 将数据库 `setting` 表作为运行时配置优先来源，配置缺失时才回退 `RuntimeProperties.WebSearchProperties` 的默认值。
- `search` 分类下的配置种子由 `backend/src/main/resources/db/migration` 和 `backend/src/main/resources/db/init.sql` 同步维护，管理端系统配置页读取同一批配置。

## Entry Points

- 聊天搜索主流程：`ChatApplicationService` 在搜索意图分支调用 `WebSearchExecutionService.search(...)`。
- 搜索通道实现：`ConfigurableWebSearchChannel.search(...)` 对 provider 响应做协议适配。
- 搜索配置读取：`RuntimeSettingService.webSearchEnabled()`、`webSearchProvider()`、`webSearchBaseUrl()`、`webSearchApiKey()`、`webSearchMaxResults()`、`webSearchLanguage()`、`webSearchCountry()`。
- 搜索引用持久化：`SearchReferenceCollector` 将最终候选来源写入消息引用表，供前端引用和历史回放使用。

## Invariants

- 搜索通道不可用、配置缺失或 provider 请求失败时，应抛出或转换为统一中文错误，不向前端暴露 provider 原始异常细节。
- 系统配置表中的密钥类配置必须走敏感配置字段，管理端只展示脱敏值，运行时通过 `RuntimeSettingService` 解密读取。
- 后处理器顺序由 Spring order 排序控制；权威最新版本排序应在普通分数重排之后运行，避免被泛化分数覆盖。
- 数据库新增或调整 `setting` 种子时，迁移脚本和 `init.sql` 初始化基线必须同步，配置说明保持中文短语。

## Extension Points

- 新增 provider 时优先扩展 `ConfigurableWebSearchChannel` 的请求构造和响应解析分支，并为 provider 增加通道级单测。
- 多 provider fallback 使用 `web_search.provider_order` 控制顺序，provider endpoint 与密钥使用 `web_search.providers.<provider>.*` 控制。
- HTML 搜索源需要结构化 HTML 解析策略，避免用脆弱正则直接截取搜索结果。
- 搜索质量调整优先放在后处理器中，保持 provider 适配只负责召回和字段归一化。

## Common Pitfalls

- 只修改旧 `web_search.provider` 默认值只能服务历史兼容；新链路的默认尝试顺序由 `web_search.provider_order` 控制。
- 只新增迁移脚本而漏改 `init.sql` 会导致新环境初始化与迁移环境配置不一致。
- HTML 搜索源结果页结构可能变化，解析逻辑必须跳过缺失标题或链接的条目，并在无结果时安全回退到后续 provider。
- 搜索配置的 `api_key`、provider 级密钥和 HTML 搜索源无密钥场景需要明确区分，不能要求所有 provider 都必须存在全局 `web_search.api_key`。
