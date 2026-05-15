# Acceptance Criteria: 聊天侧边栏会话归位

**Spec:** `docs/superpowers/specs/2026-05-15-102105-chat-sidebar-conversation-sync-design.md`
**Date:** 2026-05-15
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 已登录用户打开应用时，左侧 Sidebar 展示 `/api/chat/conversations` 返回的真实会话标题 | UI interaction | 本地存在有效登录态，接口返回至少 1 条真实会话 | 左侧出现真实会话标题，且不再出现旧假数据文案如“量子力学是什么” |
| AC-002 | 聊天页内部不再渲染独立的 Conversations 会话栏 | UI interaction | 已进入聊天视图 | 页面中不存在“Conversations / 聊天工作台”这一内部左栏结构 |
| AC-003 | 点击 Sidebar 中的真实会话时，主区与右侧回放切换到对应会话数据 | UI interaction | 已登录，接口为指定会话返回消息、步骤、来源和产物 | 主区显示该会话消息内容，右栏显示对应步骤、来源和产物 |
| AC-004 | 点击“新建对话”时，主区回到欢迎空态首页 | UI interaction | 当前已选中某个真实会话 | 主区出现欢迎空态标题与功能卡片，旧会话消息内容不再显示 |
| AC-005 | 点击“新建对话”后，左侧真实会话历史仍然可见 | UI interaction | 当前账号已有真实会话列表 | Sidebar 仍保留真实会话分组与标题，不会被清空为假数据或空白 |
| AC-006 | 在新建态首次发送消息时，前端请求不携带旧 `conversationId` | Logic | 已点击“新建对话”，当前 `activeConversationId` 为空 | 发起的 `/api/chat/stream` 请求只包含 `question` 参数，不包含 `conversationId=` |
| AC-007 | 新建态发送消息后，前端仍能通过 SSE `meta` 事件恢复新会话上下文并刷新真实列表 | Logic | `/api/chat/stream` 返回 `meta.conversationId` 与后续 `finish/done` 事件 | 刷新后新会话进入真实列表，且主区显示新回复内容 |
| AC-008 | 未登录状态下 Sidebar 不显示硬编码历史假数据 | UI interaction | 当前无有效登录态 | 左侧仅保留导航与登录入口，不出现任何旧示例会话标题 |
