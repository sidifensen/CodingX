# Chat Split Intent Routing Design

## 背景

当前聊天链路在改写后先对整句 `rewrittenQuestion` 做一次意图路由，再把拆分出的子问题交给同一个动作处理。混合问题中只要整体命中 `SEARCH`，天气类子问题也会被当成网页搜索执行，无法触发 `weather_query` MCP。

`ragent` 的链路是先 `rewriteWithSplit`，再对每个 `subQuestion` 独立执行意图识别，检索和 MCP 结果按子问题合并后再进入最终模型回答。CodingX 需要按这个顺序对齐。

## 目标

当用户一次提问包含多个子问题时，系统必须对每个子问题分别路由：

- 搜索子问题进入网页搜索。
- 天气子问题进入 `weather_query` MCP。
- 直答或系统提示子问题不强制触发搜索。
- 混合问题最终仍由模型综合所有搜索引用和 MCP 工具证据生成回答。

## 设计

在 `ChatApplicationService` 内引入轻量的子问题决策结构，封装 `question` 与 `ConversationIntentDecision`。主流程使用 `rewriteResult.subQuestions()` 作为输入；未拆分时退回 `rewriteResult.rewrite()`，从而保持单问题行为不变。

每个子问题调用 `conversationIntentService.route(question, mcpEnabled)`。若任一子问题命中 `CLARIFY`、`MCP_DISABLED` 或启用列表外的 MCP，继续沿用当前短路回复语义，避免部分执行后再提示用户配置缺失。其余情况下，按动作分组执行：`MCP` 子问题逐个调用现有 MCP 执行逻辑并向历史追加工具证据，`SEARCH` 子问题批量调用现有并行搜索逻辑。

## 兼容约束

- 原有单问题 MCP、搜索、直答、澄清行为不变。
- `mcp-call` 的 `start/progress/complete` 载荷字段保持兼容，输入问题改为命中的子问题。
- 搜索引用收集、文档产物生成和最终模型调用继续复用现有链路。
- 运行结果记录的主意图使用第一个有执行价值的子问题意图，避免数据库模型扩展。

## 测试

新增混合问题回归测试：改写拆分为 AI 模型强弱、北京天气、量子力学三个子问题；验证天气子问题调用 `weather_query`，网页搜索不包含天气问题，最终模型仍被调用。
