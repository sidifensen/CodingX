# 系统联网搜索禁用守卫设计

## 背景

用户显式选择 `web-access` 技能并输入搜索型问题时，聊天编排层会先把自然语言路由为 `SEARCH`，再调用系统内置 `WebSearchExecutionService`。当 `web_search.enabled=false` 时，搜索通道不可用会直接抛错，导致技能上下文还没有进入模型阶段。

## 目标

- `web_search.enabled=false` 时，自动搜索链路必须全面禁用：不创建搜索步骤、不调用搜索服务、不收集搜索引用、不生成搜索整理文档。
- 显式选择 `web-access` 技能时，由技能上下文接管联网语义，系统内置搜索不得抢先执行。
- 保持搜索开关开启且未选择 `web-access` 时的原有搜索链路不回归。

## 设计

在 `ChatApplicationService` 的子问题意图路由之后增加自动搜索守卫。守卫只处理已经命中 `SEARCH` 的子问题：当系统搜索关闭，或本轮技能包含 `web-access` 时，把该子问题的动作从 `SEARCH` 降级为 `DIRECT`，保留原 intentCode 便于日志和运行记录追踪。后续 `searchQuestions(...)` 因不再看到 `SEARCH` 动作而不会进入搜索执行链路。

## 边界

- 本次不新增 `web-access` 的 Java/CDP 执行器；技能仍然只作为模型上下文。
- MCP 子问题不受影响，混合问题中搜索子问题会被禁用，MCP 子问题仍按原规则执行。
- 本地运行态原本不持久化搜索步骤，本次不额外改动本地链路。
