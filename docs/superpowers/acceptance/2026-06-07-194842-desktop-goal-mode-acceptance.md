# Acceptance Criteria: Desktop Goal Mode

**Spec:** `docs/superpowers/specs/2026-06-07-194842-desktop-goal-mode-design.md`
**Date:** 2026-06-07
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 聊天流请求在桌面目标模式开启时应携带 `planMode=true`。 | Logic | 调用 `buildStreamRequestUrl`，传入 `goalModeEnabled=true`。 | 返回 URL 的 query 中包含 `planMode=true`。 |
| AC-002 | 普通模式或缺省调用不应携带 `planMode`，避免改变既有聊天行为。 | Logic | 调用 `buildStreamRequestUrl`，不传或传入 `goalModeEnabled=false`。 | 返回 URL 的 query 中不存在 `planMode`。 |
| AC-003 | 聊天页应提供可点击的桌面目标模式入口。 | UI interaction | 渲染 `ChatView`，workspace 中 `goalModeEnabled=false`。 | 页面存在 `aria-label="切换目标模式"` 的按钮，点击后调用 `setGoalModeEnabled(true)`。 |
| AC-004 | 目标模式开启或存在执行步骤时应展示右侧悬浮目标进度窗。 | UI interaction | 渲染 `ChatView`，workspace 中 `goalModeEnabled=true` 且包含至少一个执行步骤。 | 页面出现 `data-testid="goal-progress-panel"`，显示目标模式、百分比和执行步骤标题。 |
| AC-005 | 普通空闲且没有执行步骤时不展示目标进度窗。 | UI interaction | 渲染 `ChatView`，workspace 中 `goalModeEnabled=false`、`isStreaming=false`、`executionSteps=[]`。 | 页面不存在 `data-testid="goal-progress-panel"`。 |
| AC-006 | 后端规划/目标模式系统提示应适配桌面端目标模式并指示目标工具调用。 | Logic | 构造 `SendChatMessageCommand`，`planMode=true`，捕获发送给 AI 客户端的 system prompt。 | system prompt 包含“桌面端”、“get_goal”、“create_goal”、“update_goal”，并说明大型或明确目标任务应创建和跟进目标。 |
| AC-007 | 目标进度窗在亮色和暗色主题下必须使用非透明背景且文本可读。 | UI interaction | 启动前端页面，开启目标模式并产生或模拟执行步骤。 | CDP 截图或计算样式显示关键容器 `backgroundColor` 非透明，文本颜色与背景不同，证据保存到 `logs/`。 |
