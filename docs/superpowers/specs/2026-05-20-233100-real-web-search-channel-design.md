# 真实联网搜索通道设计

## 背景

当前聊天运行时已具备 `SEARCH` 意图分支、搜索后处理、引用落库与前端引用展示链路，但真实搜索源仍停留在本地 mock，实现无法回答最新公开信息。

## 目标

- 为聊天运行时接入真实联网搜索通道，替换默认 mock 搜索结果
- 保持现有 `SearchChannel -> WebSearchExecutionService -> SearchReferenceCollector` 链路不变
- 支持通过运行时配置切换搜索 provider，而不是把 provider 逻辑散落到业务编排层

## 范围

- 后端新增可配置搜索通道实现
- 新增联网搜索运行时配置与数据库种子
- 保留现有去重、重排、TopK、引用展示和文档产物逻辑

不在本次范围内：

- 自建 RAG 知识库
- 页面抓取、正文抽取、二次摘要
- 前端管理页新增搜索 provider 配置界面

## 方案

### 架构

在 `backend/src/main/java/com/codingx/chat/infrastructure/search/` 下新增 `ConfigurableWebSearchChannel`，实现 `SearchChannel` 接口。该通道负责：

- 判断真实联网搜索是否启用
- 按 provider 协议组装 HTTP 请求
- 解析 provider 响应并映射为 `SearchReferenceCandidate`
- 在请求失败、配置缺失或响应异常时返回空结果，由上层统一降级

### 配置

在 `RuntimeProperties` 和 `application.yml` 中新增 `app.runtime.web-search` 配置块，包含：

- `enabled`
- `provider`
- `base-url`
- `api-key`
- `max-results`
- `language`
- `country`

数据库 `setting` 表同步补齐 `web_search.*` 种子与迁移脚本，便于后续在运行时管理页接入。

### Provider 支持

首批支持两个 provider：

- `serper`
- `tavily`

选择依据：

- 都提供结构化搜索 API，适合作为“现查公开信息”的第一阶段实现
- 响应结构清晰，适配成本低

### 排序与重排

`tavily` 保留 provider 自带 `score`。`serper` 没有统一分数，因此用结果顺序映射为递减分值，保证现有重排处理器不会把靠前结果排到后面。

## 错误处理

- 未配置 API Key、Base URL 或 provider 时，通道直接禁用
- HTTP 非 2xx、空响应体、JSON 解析异常时，通道返回空结果
- 不向前端暴露 provider 原始错误，避免污染聊天主链路

## 测试

- 新增通道级测试，覆盖：
  - 未配置 API Key 时禁用
  - `serper` 响应解析
  - `tavily` 响应解析
- 保留并通过现有搜索编排测试，确保 `SEARCH` 主链路不回归

## 风险

- 当前默认仍为关闭，未配置环境变量或运行时设置时不会产生真实联网搜索效果
- 尚未增加 provider 级可观测性和失败统计，后续可补 trace metadata
