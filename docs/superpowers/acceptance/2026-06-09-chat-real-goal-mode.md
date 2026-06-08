# Acceptance Criteria: Chat Real Goal Mode

**Spec:** `docs/superpowers/specs/2026-06-09-chat-real-goal-mode-design.md`
**Date:** 2026-06-09
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | `create_goal` 必须把当前会话目标写入数据库，而不是进程内 Map。 | Logic | 绑定 `ChatToolExecutionContext` 的 userId、conversationId、runId 后执行 `create_goal`。 | 数据库存在一条 `chat_goal` 记录，`conversation_id` 等于当前会话，返回 metadata 中包含目标快照。 |
| AC-002 | 同一会话已有 active goal 时再次 `create_goal` 不应重复创建活跃目标。 | Logic | 当前会话已存在 `ACTIVE` 目标。 | 再次执行 `create_goal` 后 active 目标数量仍为 1，返回已有目标或更新后的同一目标。 |
| AC-003 | `update_goal` 必须更新目标状态和步骤快照，并追加事件流水。 | Logic | 当前会话存在 active goal，输入包含目标状态和 steps。 | `chat_goal` 状态/摘要更新，`chat_goal_step` 反映最新步骤，`chat_goal_event` 至少新增一条更新事件。 |
| AC-004 | `get_goal` 必须按当前会话读取目标，不能跨会话读取同名 goalKey。 | Logic | 两个会话都有 `goalKey=default` 的目标。 | 分别绑定两个 conversationId 执行 `get_goal` 时返回各自会话目标。 |
| AC-005 | 目标工具缺少聊天治理上下文时应返回中文业务错误。 | Logic | 未绑定 `ChatToolExecutionContext` 直接执行 `create_goal`。 | 抛出业务异常，错误含义为目标工具必须在聊天会话中执行。 |
| AC-006 | 目标创建或更新后必须发布 `goal` SSE 事件。 | Logic | 使用记录型 `ChatStreamPublisher` 执行 `create_goal` 或 `update_goal`。 | 发布事件包含 conversationId、goal、steps 和 eventType，前端可直接更新 active goal。 |
| AC-007 | 前端进入或切换会话时必须加载当前 active goal。 | Logic | `ChatApi.getActiveGoal` 返回 active goal。 | `useChatWorkspace` 的 `activeGoal` 被设置为后端返回目标。 |
| AC-008 | 前端收到 `goal` SSE 事件时必须更新右侧目标浮窗数据。 | Logic | 流式事件包含 `event: goal` 和目标 payload。 | `activeGoal` 更新为事件中的目标，浮窗显示新标题和步骤进度。 |
| AC-009 | 没有 active goal 时即使目标模式开关开启，也不应显示右侧目标浮窗。 | UI interaction | `goalModeEnabled=true`、`activeGoal=null`、普通流式生成中。 | 页面不存在 `data-testid="goal-progress-panel"`。 |
| AC-010 | 存在 active goal 时右侧目标浮窗必须展示目标标题、状态和步骤完成数。 | UI interaction | 渲染 `ChatView`，workspace 中含 active goal 和两个步骤。 | 页面出现 `goal-progress-panel`，展示目标标题、`1/2` 或等价完成数，以及步骤标题。 |
| AC-011 | `ChatApi.getActiveGoal` 对目标 ID 和步骤 ID 必须字符串化，避免 Long 精度问题。 | Logic | 后端返回数字或字符串 ID 混合的目标数据。 | 前端归一化后 `goal.id`、`step.id`、`conversationId` 都是字符串。 |
| AC-012 | 数据库迁移和 `schema.sql` 必须同时包含目标三张表及中文注释。 | Logic | 读取迁移脚本和基线 schema。 | `chat_goal`、`chat_goal_step`、`chat_goal_event` 均存在表注释和字段注释。 |
| AC-013 | 功能文档必须记录真实目标状态的存储、工具链路、前端展示和验证方式。 | Logic | 读取 `docs/features/chat/desktop-goal-mode.md` 与功能索引。 | 文档说明目标状态来自数据库 active goal，不再由 executionSteps 推导。 |
| AC-014 | 右侧目标浮窗在亮色和暗色主题下必须背景非透明且文本可读。 | UI interaction | 启动前端并让会话存在 active goal。 | CDP 截图或计算样式证据保存到 `logs/`，关键容器背景 alpha 非 0，文本色与背景不同。 |
