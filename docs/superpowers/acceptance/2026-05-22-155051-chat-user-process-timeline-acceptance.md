# Acceptance Criteria: Chat User Process Timeline

**Spec:** `docs/superpowers/specs/2026-05-22-155051-chat-user-process-timeline-design.md`
**Date:** 2026-05-22
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 助手消息在存在过程事件时应优先展示用户态过程时间轴 | UI interaction | 聊天消息包含思考、搜索或 MCP 过程字段 | 助手消息正文上方出现过程时间轴节点，节点文案为短句用户提示 |
| AC-002 | 思考事件不应再以长篇 reasoning 面板形式默认展示 | Logic | `thinking` 事件连续到达多个增量 | 展示模型只保留一条“正在思考”类节点，不出现完整 reasoning 长文本面板 |
| AC-003 | 搜索步骤与来源事件应合并成单条搜索进度节点 | Logic | 同一条助手消息先收到搜索 `step` 再收到多个 `reference` 事件 | 过程时间轴内只有一条搜索节点，文案可更新为“已搜索 N 个网页” |
| AC-004 | MCP 工具事件应映射为用户态实时数据节点 | Logic | 同一条助手消息收到 `mcp-call start` 与 `mcp-call complete` | 过程时间轴显示“正在获取实时数据”到“已获取数据，正在整理”的状态变化，不暴露工具编码、参数或原始结果 |
| AC-005 | 用户态主区不应展示原始工具详情文案 | UI interaction | 助手消息带有原始 `mcpCalls` 数据 | 页面中不出现 “MCP 调用”“参数”“原始结果”“元数据” 等工具详情标题 |
| AC-006 | 最终回答必须独立于过程节点渲染 | UI interaction | 助手消息同时具备过程节点和最终正文 | Markdown 正文显示在过程时间轴之后，且正文内容不包裹在过程节点容器内 |
| AC-007 | 流式完成后运行中过程节点应落为完成态且不再闪烁 | Logic | 助手消息先处于 streaming，再收到 `finish` 事件 | 过程节点状态切换为 completed，页面不再显示“正在”类运行态样式 |
| AC-008 | 过程失败时应给出用户态错误节点和原有消息错误提示 | UI interaction | 过程事件进入错误态且消息携带 `errorMessage` | 时间轴出现错误短句节点，同时消息底部仍展示现有错误提示条 |
| AC-009 | 无过程节点的普通消息仍应保持现有回答渲染 | UI interaction | 助手消息只有最终正文，无 `thinking`、`mcp-call`、`search` 相关事件 | 页面直接渲染 Markdown 回答，不产生空白过程容器 |
