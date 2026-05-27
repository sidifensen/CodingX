# AI 回复完成事件真实消息 ID 设计

## 背景

AI 回复生成完成后，前端会先把临时助手消息从 `streaming` 切到结束态并展示底部操作栏。但点赞、倒赞、重新生成等操作依赖后端落库后的数值消息 ID；当前 `finish` SSE 事件只返回 `conversationId`、`content`、`title`，导致操作栏出现时部分按钮仍处于禁用状态，直到后续历史回放接口返回真实消息。

## 目标

后端在助手消息落库后，通过 `finish` SSE 事件同步返回真实 `assistantMessageId`。前端收到该字段后立即把当前临时助手消息 ID 回填为真实 ID，使操作栏在生成结束后可立即使用。

## 设计

- 后端 `ChatStreamPublisher` 新增带 `assistantMessageId` 的完成事件契约，并保留旧三参方法作为本地临时会话或无落库消息场景的兼容入口。
- 云端聊天链路在 `chatMessageRepository.save(assistantMessage)` 后使用 `assistantMessage.getId()` 发布完成事件。
- `SseChatStreamPublisher` 在 `assistantMessageId` 非空时写入 `finish` 载荷；本地临时会话不写该字段，避免前端误认为本地消息可调用云端反馈接口。
- 前端 `useChatWorkspace` 处理 `finish` 时，如果收到合法 `assistantMessageId`，立即更新当前乐观助手消息的 `id` 与 `conversationId`，同时保留已有正文、搜索来源、过程卡片和时间线。

## 验证

- 前端 hook 测试覆盖：`finish` 带 `assistantMessageId` 时，当前助手消息立刻替换为真实消息 ID。
- 后端 publisher 测试覆盖：完成事件载荷包含 `assistantMessageId`。
- 仅运行相关前端测试与后端测试，避免扩大验证面影响其他会话改动。
