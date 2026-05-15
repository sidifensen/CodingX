# Chat Runtime Ragent Parity Acceptance

## 验收范围

本轮验收覆盖 `ragent` 运行时能力迁移中的 1/2/3/4/5/6/7/9 项。

## 验收标准

1. 队列限流
- 当 `app.runtime.use-redis-queue-gate=true` 且 `queue-max-concurrent=1` 时，并发两条聊天请求只能有一条进入执行，另一条收到 `reject busy`。
- 等待中的请求不会阻塞已执行请求释放 permit。

2. Trace 自动采集
- 成功聊天链路会写入 `chat_trace_run`。
- `rewrite-with-split`、`intent-classify`、`intent-guidance`、`mcp-execute`、`web-search`、`reference-collect`、`artifact-generate` 等节点按实际链路落入 `chat_trace_node`。

3. 改写与意图链路
- 术语归一化命中后可复用缓存结果。
- `guidance-ambiguity-check.st` 可用于边界歧义二次判定。
- SYSTEM、SEARCH、MCP 三类意图可分流到对应动作。

4. MCP 工具链路
- 问题 `销售总额是多少` 会产生 `mcp` 步骤事件。
- 最终助手消息直接返回工具结果，不再走模型自由生成兜底。

5. 示例问题与欢迎屏
- `/api/chat/sample-questions` 返回已启用示例问题。
- 前端欢迎态能加载并展示示例问题。

6. 构建与测试
- `backend`: `mvn compile`、`mvn test`
- `frontend/user`: `npm run test:run`、`npm run build`

## 运行态证据

验收通过前，必须保留以下至少一项本地证据：

- `logs/mcp-sales-stream.txt`
- `logs/search-runtime-recheck-stream.txt`
- `chat_trace_run` / `chat_trace_node` 查询结果
- 并发 `reject busy` 事件结果
