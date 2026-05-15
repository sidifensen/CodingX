# Remaining Ragent Runtime Parity Design

## 背景

上一轮已经补齐了模型路由、首包探测、熔断降级、Redis 队列限流、Trace AOP、改写/意图/MCP/示例问题链路，但与 `ragent` 相比仍有一批关键能力缺口：

- 多搜索源通道化 + 去重 / rerank 后处理链
- 文件存储抽象，当前 `docx` 仍是本地路径
- Trace 管理页 / 查询 UI
- 意图树后台、关键词映射后台、系统设置后台、Dashboard
- token 预算 / 响应清洗 / 更细粒度线程池治理
- 前端 `thinking` 展示、停止生成交互
- 欢迎屏浏览器级视觉验证证据

本轮目标是把这些剩余项做成一套可运行、可回放、可管理的聊天运行时后台与前台能力。

## 目标

1. 将搜索执行从“首个 provider 命中即返回”升级为“多通道并行 -> 去重 -> rerank -> 收口”。
2. 将产物写入从硬编码本地路径升级为可替换的存储抽象。
3. 为聊天运行时提供可维护的后台：Trace 列表/详情、意图树管理、关键词映射管理、系统设置、Dashboard。
4. 补齐用户前端对 `thinking`、停止生成、欢迎屏视觉状态的完整体验与验证闭环。
5. 在运行时基础层补上 token 预算、响应清洗和线程池分工，减少长链路不稳定因素。

## 总体方案

### 1. 搜索执行管道化

把当前 `WebSearchExecutionService` 拆成三层：

- `SearchChannel`：每个搜索源一个通道，输入统一 `SearchRequestContext`
- `SearchPipelineService`：并行调度通道，汇总候选
- `SearchResultPostProcessor`：按责任链做去重、rerank、截断

后处理顺序：

1. `DeduplicationPostProcessor`
2. `RerankPostProcessor`
3. `TopKTruncationPostProcessor`

保留现有 `SearchReferenceCollector` 和 `DocumentArtifactService` 作为下游消费者，只改变其输入质量。

### 2. 文件存储抽象

新增 `FileStorageService` 领域接口，最小方法：

- `saveArtifact(...)`
- `loadArtifact(...)`
- `buildPublicPath(...)`

首版提供：

- `LocalFileStorageService`

`DocumentArtifactService` 不再拼硬编码 `artifacts/...` 路径，而是通过存储服务拿到 `storagePath` 和可访问路径。这样后续接 S3/RustFS 时无需再动聊天主链路。

### 3. Token 预算、响应清洗与线程池治理

新增：

- `TokenCounterService`：先做启发式 token 估算
- `LlmResponseCleaner`：清理首尾空白、NUL 字符、异常分隔符和重复换行
- `ChatExecutorConfig`：把聊天入口、搜索通道、MCP 执行拆分成独立线程池 Bean

搜索多子问题、搜索多通道与 MCP 不再混用 `CompletableFuture.supplyAsync()` 的公共池。

### 4. 后台查询与管理 API

新增后台 API：

- `GET /api/admin/chat/traces`、`GET /api/admin/chat/traces/{traceId}`
- `GET/POST/PUT /api/admin/chat/intents`
- `GET/POST/PUT /api/admin/chat/query-term-mappings`
- `GET/PUT /api/admin/chat/settings`
- `GET /api/admin/chat/dashboard`

系统设置首版落在 `chat_runtime_setting` 新表，管理项包含：

- 搜索 topK
- rerank 开关
- token 预算
- thinking 展示开关
- 队列并发与超时参数

### 5. 管理端页面

管理端新增五个真实页面：

- Dashboard
- Trace 列表/详情
- 意图树管理
- 关键词映射管理
- 系统设置

页面统一走现有 `frontend/admin` 路由和布局体系，保持浅色/深色兼容。Dashboard 不做假数据，直接消费后端聚合接口。

### 6. 用户端聊天体验

`ChatView` / `useChatWorkspace` 补齐：

- SSE `thinking` 事件消费
- 助手消息 `thinkingContent` 展开区
- 停止生成状态过渡与按钮细化
- 欢迎屏示例问题视觉核验

后端若收到 `thinking` 增量，要在 SSE 中发出 `thinking` 事件，并在 `chat_message` 持久化完整 `thinking_content` / `thinking_duration`。

## 数据变更

新增表：

- `chat_runtime_setting`

已有表复用：

- `chat_trace_run`
- `chat_trace_node`
- `chat_intent_node`
- `chat_query_term_mapping`
- `chat_message_artifact`

## 验证策略

1. 搜索后处理、token 预算、响应清洗、存储抽象全部先补失败测试。
2. 后台 API 至少做 controller/service 单测。
3. 前端 user/admin 分别跑测试与构建。
4. 浏览器验证使用 `/web-access` + CDP：
   - 用户端 thinking 展示 / 停止生成 / 欢迎屏截图
   - 管理端 Trace / 意图树 / 映射 / 设置 / Dashboard 页面截图

## 范围控制

本轮不做：

- 真正对象存储接入（只做抽象 + 本地实现）
- 真正第三方 rerank 服务（先做路由接口 + mock/noop）
- 通用权限模型扩展
