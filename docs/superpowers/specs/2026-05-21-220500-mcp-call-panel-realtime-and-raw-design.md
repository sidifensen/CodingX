# MCP调用面板增强与实时状态回传设计

## 背景与问题

当前用户端 MCP 调用面板存在两类体验缺陷：

1. 信息重复：面板“输入/返回”基本复述用户问题与助手最终回答，缺少真正可诊断的工具参数与工具原始结果。
2. 反馈滞后：面板通常在 MCP 工具完成后才出现，用户在耗时调用阶段看不到“正在调用 MCP”的即时反馈。

这导致 MCP 面板对排障与可观测价值偏低。

## 目标

1. MCP 调用开始即在前端出现状态反馈（调用中）。
2. 面板内容优先展示 MCP 参数、原始结果与元数据，而非仅输入/最终回答。
3. 保持向后兼容：旧格式 `mcp-call` 事件仍可展示。
4. 不改变现有对话主流程成功语义（助手消息仍可使用工具结果内容）。

## 非目标

1. 本次不做跨刷新持久化 `mcpCalls` 存储结构改造。
2. 不引入新的数据库表或字段。
3. 不改造非 MCP 意图链路。

## 方案概览

### 1. 后端 SSE 事件改造（两阶段）

在 MCP 意图分支中，针对同一次工具执行统一生成 `callId`，并发送两次 `mcp-call`：

- `phase=start`：工具执行前发送，字段包含 `callId/toolId/displayName/params/startedAt`。
- `phase=complete`：工具执行后发送，字段包含 `callId/.../rawResult/resultMetadata/finishedAt`。

兼容字段 `input/content/metadata` 继续保留，避免旧前端解析失败。

### 2. 前端流式聚合改造

`useChatWorkspace` 中的 `mcp-call` 处理由“无脑 append”改为：

- 若存在 `callId`，按 `callId` upsert 合并，确保 start/complete 更新同一条记录。
- 若无 `callId`（旧事件），沿用 append 逻辑。
- `status` 根据 `phase` 推导（running/completed/error），收到 start 即写入消息 `mcpCalls`。

### 3. MCP 面板展示改造

`ChatView` 的 `McpCallPanel` 调整：

- 顶部状态由“消息是否 streaming”扩展为“是否存在 running 调用”。
- 调用项正文优先展示：
  - `参数`（`params`）
  - `原始结果`（`rawResult` 或 `content`）
  - `元数据`（`resultMetadata` 或 `metadata`）
- 对象类型统一 JSON pretty-print，保持可读性。

## 关键数据结构变更

前端 `McpCallItem` 增加可选字段：

- `callId?: string`
- `phase?: 'start' | 'complete' | 'error'`
- `status?: 'running' | 'completed' | 'error'`
- `params?: Record<string, unknown> | string`
- `rawResult?: unknown`
- `resultMetadata?: Record<string, unknown>`
- `startedAt?: string`
- `finishedAt?: string`
- `errorMessage?: string`

保持 `input/content/metadata` 原字段兼容。

## 错误处理与边界

1. 后端异常仍遵循现有全局异常链路，不引入新异常类型。
2. 前端解析 `params/rawResult/resultMetadata` 时严格容错，非对象/非字符串一律安全序列化。
3. 若仅收到 `start` 未收到 `complete`（异常中断），面板保持“调用中”并随消息状态进入取消/错误态。

## 测试策略

### 后端

- `ChatApplicationMcpFlowTest`：新增断言，验证 `publishMcpCall` 至少包含 `phase=start` 和 `phase=complete` 两次调用，且 `callId` 一致。

### 前端 Hook

- `useChatWorkspace.test.ts`：构造 `mcp-call start -> complete` 事件序列，断言：
  - start 到达即生成 1 条 running 调用；
  - complete 到达后仍为 1 条记录且状态转 completed，参数与原始结果可读。

### 前端视图

- `ChatView.test.tsx`：断言 MCP 面板显示“参数/原始结果/元数据”并渲染结构化字段。

## 回滚策略

若上线后出现兼容问题，可临时回退前端 `callId` 合并逻辑为 append；后端保持发送兼容字段不影响旧逻辑。

## 影响评估

- 后端：`ChatApplicationService` MCP 分支事件编排增强。
- 前端：聊天页流式事件聚合与 MCP 面板展示增强。
- 现有接口签名不变，SSE 事件字段扩展为向后兼容。
