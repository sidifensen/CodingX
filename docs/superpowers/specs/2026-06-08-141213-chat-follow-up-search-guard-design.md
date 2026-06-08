# Design: Chat Follow-up Search Guard

**Date:** 2026-06-08
**Status:** Approved

## Problem

用户在代码生成会话中先要求“写个好看的 html”，下一轮输入“丰富一下”时，当前后端仍会把改写后的短句交给通用意图分流。若分类模型或兜底候选把该短句判成 `SEARCH`，`ChatApplicationService` 会执行系统联网搜索，导致本应基于上一轮代码产物继续编辑的请求变成网页查询。

## Goal

在聊天主流程中保护“会话延续/编辑类短指令”：当上一轮上下文已经是代码生成、文件写入或本地工具产物时，当前轮短句如“丰富一下”“完善一下”“继续优化”即使命中 `SEARCH`，也应降级为普通直答并交给模型结合历史继续处理。

## Non-Goals

- 不关闭 `web_search.enabled` 总开关。
- 不改变显式搜索请求，例如“搜一下今天新闻”“查询最新 Java 版本”。
- 不改数据库意图树种子，不引入新的意图类型。
- 不修改前端交互或浏览器验证链路。

## Design

1. 在 `ChatApplicationService` 的搜索决策归一化阶段增加“上下文延续短指令”保护。该保护只读取当前会话历史和本轮路由出的子问题，命中后把 `SEARCH` 决策替换为 `DIRECT`，保留原问题作为模型输入。
2. 保护条件必须同时满足：本轮问题是短编辑/延续指令；原始用户输入没有显式搜索、新鲜度或联网词；历史中存在代码/文件产物信号，例如 `html`、`index.html`、`已写入`、`文件已更新`、`shell_command`、`apply_patch` 等。
3. 真实搜索类问题仍走原链路。只要当前输入包含“搜索”“查询”“最新”“今天”“网页”“联网”等显式信息检索词，就不触发保护。
4. 回归测试放在 `ChatApplicationSearchFlowTest`。测试构造已有 HTML 产物历史、当前输入“丰富一下”、意图服务返回 `SEARCH`，断言不会调用 `WebSearchExecutionService`、不会写搜索引用或文档产物，但仍会进入普通模型回答。

## Data Flow

1. 前端发送第二轮短句后，`ChatApplicationService.sendMessage` 读取历史消息并保存本轮用户消息。
2. `ConversationRewriteService` 返回本轮改写结果和子问题列表；`ConversationIntentService` 对子问题做意图分流。
3. `ChatApplicationService` 在自动搜索守卫前检查短指令、显式搜索词和历史产物信号；命中保护时把该子问题的 `SEARCH` 决策改为 `DIRECT`。
4. 后续 `searchQuestions(...)` 得到空列表，因此不会调用搜索 provider、引用收集和搜索整理文档；模型历史仍包含原有消息和本轮短句，模型可继续编辑上一轮产物。

## Testing

- `mvn -Dtest=ChatApplicationSearchFlowTest#sendMessageDoesNotInvokeSearchForCodeArtifactFollowUpShortEdit test`
- `mvn -Dtest=ChatApplicationSearchFlowTest test`
- `mvn compile`
- `mvn test`
