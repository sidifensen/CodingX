# Chat Interleaved Process Design

## 目标

修复助手消息内“深度思考 / 工具调用 / 搜索网页”全部堆在正文上方的问题，让流式过程中到达的正文片段与过程节点按事件顺序交错渲染，形成 Codex 风格的同一条消息内时间线。

## 范围

- 仅修改用户端聊天前端。
- 不调整后端 SSE 协议、数据库结构或工具执行逻辑。
- 保留 `content` 与 `processCards` 字段，兼容历史回放、分享预览和导出逻辑。

## 设计

`ChatMessageItem` 新增可选 `timelineItems`，运行时记录同一条助手消息内的顺序片段。正文片段使用 `type: "content"` 存储 Markdown 文本，过程片段使用 `type: "process"` 指向对应 `ProcessCardItem`。流式事件消费时，`message` 事件追加到最近的正文片段；`thinking`、`tool-call`、`mcp-call`、`step(search)`、`reference` 事件写入或更新过程片段，已存在的过程卡片只更新内容，不移动位置。

渲染层优先使用 `timelineItems` 输出混合时间线。连续过程片段继续复用已有 `ProcessTracePanel` 分组能力，因此连续搜索结果仍折叠为“已搜索网页 N 次”，连续命令仍折叠为“已运行 N 条命令”。没有 `timelineItems` 的历史消息继续走旧的 `processCards + content` 兜底，避免刷新后丢失可见过程。

## 边界

- `finish`、`cancel`、`error` 只收敛状态并更新已有过程片段状态，不改变已记录的顺序。
- 如果 `finish.content` 比流式正文更完整且此前没有正文片段，则补一个正文片段；否则不重排已有时间线。
- 过程时间线只保存前端运行时展示顺序，暂不作为后端持久化协议。

## 验证

- 单元测试覆盖流式事件顺序：正文片段 -> 工具过程 -> 正文片段。
- 组件测试覆盖 DOM 文本顺序，确保过程节点出现在两段正文之间。
- 前端测试、构建和 CDP 浏览器截图验证聊天页渲染。
