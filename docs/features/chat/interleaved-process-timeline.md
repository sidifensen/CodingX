# 助手消息过程时间线穿插展示

## 功能用途

聊天助手消息支持把正文、深度思考、网页搜索和工具调用按流式事件到达顺序穿插展示，避免所有过程块固定堆在最终回答上方。

## 使用入口

用户在聊天页发送消息后，前端消费 SSE 流中的 `message`、`thinking`、`tool-call`、`mcp-call`、`step(search)` 和 `reference` 事件，并在同一条助手消息中实时更新展示。

## 核心流程

1. 发送消息时创建乐观助手消息，并初始化空 `timelineItems`。
2. `message` 事件把正文 delta 追加到最近的正文片段；如果前一片段是过程节点，则创建新的正文片段。
3. 思考、工具、搜索和来源事件继续更新兼容字段 `processCards`，同时把新增或变更的过程卡片同步到 `timelineItems`。
4. 渲染层优先按 `timelineItems` 输出正文和过程节点；没有时间线的历史消息继续使用 `processCards + content` 兜底。
5. 连续网页搜索结果和连续命令过程仍复用已有汇总组件，显示为“已搜索网页 N 次”或“已运行 N 条命令”。
6. 工具模式下，后端保留工具调用前已经发布的正文和 thinking；工具结果回灌后，后续模型正文继续追加到同一条助手消息。
7. `finish` 事件只做状态收口和缺失尾段补齐，不重建已到达的时间线，避免工具前正文在流结束后丢失。
8. 工具事件中的 `reactThought` 只作为工具阶段元数据，不等价于模型真实 thinking；前端只有收到 `thinking` SSE 时才展示深度思考内容。
9. 工具回灌下一轮模型前，后端会把本轮已流式展示给用户的正文写入模型上下文，约束模型只继续未完成步骤，避免重复输出相同开场白。

## 关键文件

- `frontend/user/src/views/chat/types.ts`：定义 `MessageTimelineItem` 与 `ChatMessageItem.timelineItems`。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：维护流式消息正文、过程卡片和时间线片段。
- `frontend/user/src/views/ChatView.tsx`：按时间线渲染助手消息体，并兼容历史回放。
- `frontend/user/tests/views/chat/useChatWorkspace.test.ts`：验证流式事件顺序写入时间线。
- `frontend/user/tests/views/ChatView.test.tsx`：验证 DOM 交错顺序和连续搜索折叠。

## 关键数据结构

`timelineItems` 只用于前端运行时和本地快照展示顺序，不改变后端协议。正文片段结构为 `{ type: "content", content }`，过程片段结构为 `{ type: "process", card }`，其中 `card` 复用既有 `ProcessCardItem`。

## 测试与验证

使用 Vitest 覆盖流式顺序、消息体 DOM 顺序和连续搜索折叠。涉及前端交互展示变更时，还需要通过 CDP 打开聊天页并保存截图到 `logs/`。
