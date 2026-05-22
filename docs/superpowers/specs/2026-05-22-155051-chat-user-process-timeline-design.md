# Chat User Process Timeline Design

## 背景

当前聊天主区会把以下几类过程信息直接暴露给用户：

1. `thinkingContent` 长文本思考块。
2. `McpCallPanel` 工具调用详情卡片，包含参数、原始结果、元数据。
3. `SearchProgressPanel` 搜索进度面板。

这套表达更接近调试面板，不适合默认用户态，主要问题是：

- 过程文本过长，容易与最终回答重复。
- 工具细节过于底层，普通用户不关心 `toolId`、参数或原始返回。
- 搜索、MCP、思考三个面板并列，缺少统一时间顺序与叙事节奏。

## 目标

1. 聊天主区改为纯用户态过程时间轴。
2. 过程展示只保留短过程节点、搜索进度节点与最终回答。
3. 不展示原始 MCP 细节、长 reasoning 文本和调试信息。
4. 保持现有暗色主题、三栏结构与右栏来源/产物区不变。
5. 兼容当前 SSE 协议，优先通过前端映射现有事件完成改造。

## 非目标

1. 本次不新增调试态或开发者开关。
2. 本次不改造管理端 Trace 页面。
3. 本次不要求后端新增数据库字段。
4. 本次不改造右栏“参考信息 / 任务产物”信息架构。

## 用户体验原则

- 过程可见，但不冗长。
- 阶段清晰，但不暴露技术实现。
- 最终回答必须独立、完整、稳定。
- 同一时刻最多让用户理解一个阶段动作。

## 方案概览

### 1. 引入用户态过程节点模型

在前端消息模型内新增 `processTimeline`（名称可按现有代码风格微调），用于承载主区过程节点。每个节点只允许以下类型：

- `status`: 短状态说明，例如“正在思考”“正在整理答案”。
- `search`: 搜索进度节点，例如“已搜索 6 个网页”。
- `tool`: 泛化后的实时数据节点，例如“正在获取实时数据”“已获取数据，开始整理”。

节点字段应至少包含：

- `id`
- `kind`
- `text`
- `state` (`running` / `completed` / `error`)
- `createdAt` 或可排序序号

### 2. 现有 SSE 事件映射为用户文案

前端继续消费现有 `thinking`、`mcp-call`、`step`、`reference`、`finish` 事件，但不再把底层负载原样透出，而是映射为短句节点：

- `thinking` 首次到达：插入“正在思考”节点；后续增量不再展开长文本。
- 搜索步骤开始：插入“正在搜索相关资料”节点。
- 搜索来源增加：更新为“已搜索 N 个网页”。
- `mcp-call start/progress`：插入或更新“正在获取实时数据”节点。
- `mcp-call complete`：更新为“已获取数据，正在整理”。
- `finish`：结束所有运行中节点，仅保留最终回答正文。

### 3. 统一时间轴渲染

`ChatView` 中助手消息改为：

1. 过程时间轴
2. 附件（如有）
3. 最终回答 Markdown
4. 消息操作区

原 `ThinkingPanel` 与 `McpCallPanel` 不再作为默认用户视图渲染；搜索进度也并入统一时间轴，而不是独立大面板。

### 4. 保留右栏信息架构

右栏仍保留：

- 任务步骤
- 参考信息
- 任务产物

主区隐藏原始调用细节后，右栏仍承担来源可信度与产物回放职责，避免主区丢失上下文支撑。

## 组件与数据改造

### 前端类型

修改 `frontend/user/src/views/chat/types.ts`：

- 在 `ChatMessageItem` 上新增 `processTimeline?: ChatProcessTimelineItem[]`
- 新增 `ChatProcessTimelineItem` 类型
- 保留 `thinkingContent`、`mcpCalls`、`searchProgress` 作为兼容输入，但标注为底层流式来源而非默认展示字段

### 前端 Hook

修改 `frontend/user/src/views/chat/useChatWorkspace.ts`：

- 为当前助手消息维护可增量 upsert 的过程节点集合
- 把 `thinking`、`mcp-call`、`step`、`reference`、`finish` 映射为用户态节点
- 节点更新必须幂等，避免重复插入“正在思考”“正在搜索相关资料”
- 搜索来源数量变化应更新同一节点，而不是追加多条相似文案

### 前端视图

修改 `frontend/user/src/views/ChatView.tsx`：

- 用新的 `ProcessTimeline` 组件替代 `ThinkingPanel`、`McpCallPanel`、`SearchProgressPanel` 的默认渲染位置
- 时间轴节点默认展开，无折叠开关
- 节点使用低干扰视觉层级，不抢正文焦点
- 错误态节点使用现有主题中的红色语义令牌

## 展示规则

1. 若消息有过程节点，先渲染过程时间轴。
2. 若消息无过程节点，直接渲染最终回答。
3. 过程节点文本长度控制在一行到两行，不允许多段解释。
4. 最终回答不应复述“我先搜索了什么工具”，只保留结论与必要依据。
5. 流式完成后，运行中节点需稳定落为完成态，避免视觉闪烁。

## 错误与边界

1. 若搜索或 MCP 中途失败，时间轴显示一条错误节点，如“获取实时数据失败”，并保留消息级错误提示。
2. 若只有最终回答没有任何过程节点，界面行为不变。
3. 若 `thinking` 事件大量到达，也不能在主区展示长篇 reasoning；最多只维持“正在思考”节点。
4. 若 `reference` 先于搜索步骤进入，前端仍需补建搜索节点并显示累计数量。

## 测试策略

### Hook 单测

覆盖 `useChatWorkspace`：

- `thinking` 事件只生成短过程节点，不把增量长文本暴露到主区展示模型。
- 搜索步骤与来源事件应合并为单条搜索节点，并能正确累积网页数量。
- `mcp-call` 事件应映射为“获取实时数据”类节点，而非展示参数与原始结果。

### 视图单测

覆盖 `ChatView`：

- 助手消息优先渲染时间轴而非旧思考/工具大面板。
- 页面不再出现“MCP 调用”“参数”“原始结果”等用户态禁用文案。
- 最终回答仍正常渲染 Markdown。

### 验证命令

- `cd frontend/user && npm run test:run -- src/views/ChatView.test.tsx src/views/chat/useChatWorkspace.test.ts`
- `cd frontend/user && npm run build`

## 影响评估

- 主要影响用户端聊天页的消息过程呈现方式。
- 不改变发送接口，不要求后端同步改协议即可先落地。
- 若后续需要更细颗粒度文案，可再新增专用 `process-note` 事件，但不作为本次前置依赖。
