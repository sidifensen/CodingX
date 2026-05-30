---
type: contract
title: chat-web-search-runtime-contract
summary: 记录联网搜索从系统配置到标准来源候选的运行时契约
tags:
  - chat
  - search
owned_paths:
  - backend/src/main/java/com/codingx/chat/application/service/search
  - backend/src/main/java/com/codingx/chat/infrastructure/search
  - backend/src/main/resources/db/migration/*web_search*
related_docs:
  - docs/superpowers/memory/chat/web-search-module-card.md
entrypoints:
  - backend/src/main/java/com/codingx/chat/application/service/search/SearchChannel.java
  - backend/src/main/java/com/codingx/chat/application/service/search/SearchRequestContext.java
  - backend/src/main/java/com/codingx/chat/application/service/search/SearchReferenceCandidate.java
last_verified_commit: a7626895
status: active
---

# Chat Web Search Runtime Contract

## Scope

该契约覆盖后端聊天联网搜索运行时：系统配置读取、搜索通道启用判断、provider 请求、结果归一化、后处理排序和引用候选输出。

## Producers And Consumers

- Producer：`ConfigurableWebSearchChannel` 生产 `SearchReferenceCandidate` 列表。
- Coordinator：`WebSearchExecutionService` 选择启用通道、执行通道、合并结果并调用 `SearchResultPostProcessor`。
- Consumers：聊天搜索分支消费最终候选来源，`SearchReferenceCollector` 负责落库，前端消息引用面板负责展示。

## Interface Rules

- `SearchChannel.isEnabled(context)` 必须只基于配置和上下文判断是否可用，不应发起外部网络请求。
- `SearchChannel.search(context)` 返回标题、URL、站点名、摘要和分数已归一化的候选列表；无效条目必须在通道内过滤。
- `SearchReferenceCandidate.url()` 是后续去重和引用展示的关键字段，不能为空。
- `SearchRequestContext.timeoutMs()` 是单次搜索链路预算，provider HTTP 调用需要尊重该超时。

## Configuration Rules

- 当前稳定配置键包括：`web_search.enabled`、`web_search.provider_order`、`web_search.failure_threshold`、`web_search.open_duration_ms`、`web_search.providers.<provider>.base_url`、`web_search.providers.<provider>.api_key`、`web_search.max_results`、`web_search.language`、`web_search.country`。
- `web_search.provider`、`web_search.base_url`、`web_search.api_key` 是旧单 provider 配置，仍作为历史兼容兜底。
- `web_search.providers.*.api_key` 和旧 `web_search.api_key` 是敏感配置，迁移后应写入 `encrypted_value` 并保留 `masked_value` 供管理端展示。
- 搜索质量配置 `search.top_k`、`search.rerank_enabled`、`search.timeout_ms` 与 provider 配置分离，分别服务截断、重排和超时控制。

## Invariants

- `web_search.enabled=false` 时，内置系统搜索不得自动向外部 provider 发起请求。
- provider 进入 OPEN 熔断状态时，搜索链路必须跳过该 provider；冷却结束后只能放行一次 HALF_OPEN 探测。
- provider 不支持、base URL 非法、HTTP 失败、响应为空或 JSON 解析失败时，应被统一转换为搜索不可用异常。
- 搜索通道可以返回空列表，但如果所有启用通道都失败且没有结果，执行服务应向上抛出搜索不可用异常。
- 后处理器只能处理标准候选列表，不应反向依赖 provider 原始响应结构。

## Compatibility Notes

- `RuntimeSettingService` 在系统配置缺失时会回退 `RuntimeProperties.WebSearchProperties`，因此新增配置必须同时考虑数据库种子和代码默认值。
- 旧环境可能仍存在未迁移的明文配置字段，配置仓储已包含敏感字段缺失时的兼容读取逻辑。
- 迁移脚本需要使用 `ON CONFLICT (setting_key) DO UPDATE` 保证重复执行和旧环境升级时配置元数据保持一致。
