# Chat Runtime Ragent Parity Design

## 背景

本轮对齐面向 `ragent` 在聊天运行时链路上的高价值能力，目标不是引入完整 RAG / 知识库体系，而是把 `CodingX` 当前聊天、搜索、MCP、Trace 和欢迎屏能力提升到可持续扩展、可真实运行验证的状态。

用户明确要求优先补齐以下能力：

- Redis 排队限流
- Trace 注解与 AOP 自动采集
- 查询改写、多问题拆分与术语缓存
- `guidance-ambiguity-check.st` 二次歧义判定
- MCP 意图与工具执行链路
- 示例问题服务、接口与欢迎屏

## 目标

1. 让模型、意图、搜索、MCP、Trace 和欢迎屏形成一条可跑通的真实链路。
2. 尽量复用 `ragent` 设计与提示词资产，减少自创行为差异。
3. 所有新能力必须同时补齐：配置接入、数据库结构、种子数据、单测/集成验证、运行态验证。

## 方案

### 1. 运行时门控

聊天门控继续保留 `ConversationQueueGate` 作为统一入口，但在 Redis 模式下禁止持有 JVM 级锁进行轮询等待，避免等待请求反向阻塞 `release()`。运行态通过 `app.runtime.use-redis-queue-gate`、`queue-max-concurrent`、`queue-acquire-timeout-ms`、`queue-poll-interval-ms` 和 `queue-lease-seconds` 控制并发与租约。

### 2. Trace 自动采集

使用 `@ConversationTraceNode` + `ConversationTraceAspect`，把改写、意图分类、歧义引导、搜索、来源收集、产物生成、MCP 执行等关键节点自动写入 `chat_trace_node`。入口 `chat-entry` 继续由 `ChatStreamExecutionService` 创建和收口 `chat_trace_run`。

### 3. 改写与意图链路

沿用 `PromptTemplateLoader` 读取 `ragent` 风格提示词模板，`ConversationRewriteService` 输出 `ConversationRewriteResult`，支持：

- 术语归一化缓存
- Query rewrite
- 多问题拆分
- 简单销售问句的 rewrite bypass
- `guidance-ambiguity-check` 二次判定
- SYSTEM / SEARCH / MCP 分流

### 4. MCP 执行栈

`ConversationIntentService` 在命中 `mcp` 节点时，不再落入模型自由生成，而是通过：

- `ChatIntentNode.mcpToolId`
- `ChatMcpToolRegistry`
- `ChatMcpExecutionService`
- `ChatMcpToolExecutor`

执行工具并把结果写回步骤事件与最终助手消息。数据库需要给 `chat_intent_node` 增加 `mcp_tool_id` 和 `param_prompt_template`，同时把 `sales` / `sales-data` 种子数据启用并回填工具配置。

### 5. 示例问题与欢迎屏

后端新增 `chat_sample_question` 的查询服务与 `/api/chat/sample-questions` 接口；前端 `useChatWorkspace` 在 bootstrap 时并行拉取会话列表和示例问题，在欢迎态展示可点击问题卡片，作为 `ragent` 欢迎屏的轻量适配版本。

## 数据与配置变更

- `backend/.env` 提供本地模型供应商 Key 与 Redis runtime 开关
- `backend/src/main/resources/application.yml` 通过 `.env` / `backend/.env` 导入运行时配置
- `chat_intent_node` 需要新增 `mcp_tool_id`、`param_prompt_template`
- `init.sql` 与增量迁移必须同时回填 `sales-data -> sales_query`

## 验证策略

1. 单测先锁住 Redis 门控释放不被等待线程阻塞。
2. `mvn compile`、`mvn test`、`frontend/user npm run test:run`、`frontend/user npm run build` 全量通过。
3. 本地运行态验证至少覆盖：
   - 示例问题接口
   - `销售总额是多少` 的 MCP 流
   - `请介绍一下 OA 系统` 的搜索流
   - Trace 节点自动采集
   - 并发请求触发 `reject busy`

## 本轮明确不做

- `ragent` 多通道检索后处理链（去重 / rerank）
- 文件存储抽象（S3 / 对象存储）
- Trace 后台页面、意图树编辑器、关键词映射后台
- Token 预算、响应清洗、更多线程池治理
