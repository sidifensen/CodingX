# Acceptance Criteria: 管理端意图树 ragent 对齐

**Spec:** `docs/superpowers/specs/2026-05-16-020500-admin-intent-tree-ragent-parity-design.md`
**Date:** 2026-05-16
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 后端完整树接口按父子关系返回 `children`。 | API | 存在根节点 `sales` 和子节点 `sales-data`。 | `GET /api/admin/chat/intents/tree` 返回 `sales.children[0].intentCode=sales-data`。 |
| AC-002 | 创建节点时 `kind` 会派生兼容运行时的 `intentType`。 | Logic | 创建请求包含 `kind=2` 且未传 `intentType`。 | 保存后的节点 `intentType=mcp` 且 `kind=2`。 |
| AC-003 | 旧调用只传 `intentType` 时仍可派生 `kind`。 | Logic | 保存请求包含 `intentType=system` 且未传 `kind`。 | 保存后的节点 `kind=1` 且 `intentType=system`。 |
| AC-004 | 重复 `intentCode` 创建会返回中文业务错误。 | API | 未删除节点已存在相同 `intentCode`。 | 创建请求失败，响应 `success=false` 且 message 包含 `意图标识已存在`。 |
| AC-005 | 删除有子节点的节点会被阻止。 | Logic | 目标节点下存在未删除子节点。 | 删除抛出业务异常，错误文案要求先删除或迁移子节点。 |
| AC-006 | 数据库结构包含 ragent 对齐字段和中文注释。 | Logic | 读取 `schema.sql` 和新增迁移脚本。 | `chat_intent_node` 同时包含 `level`、`kind`、`examples`、`collection_name`、`top_k`、`prompt_snippet`、`sort_order` 及对应 `COMMENT`。 |
| AC-007 | 管理端页面按左树右详情结构展示节点。 | UI interaction | Mock API 返回包含 `sales` 与 `sales-data` 的树。 | 页面显示 `意图树结构` 和 `节点详情` 两个区域，点击子节点后右侧展示 `sales-data`。 |
| AC-008 | 弹窗编辑表单包含基础信息、描述与示例、Prompt 配置和高级设置。 | UI interaction | 用户点击 `编辑节点`。 | 自定义弹窗打开，能看到 `节点名称`、`示例问题`、`Prompt 配置`、`节点 TopK` 字段。 |
| AC-009 | 前端创建 MCP 节点前校验工具 ID。 | UI interaction | 新建节点表单选择 `MCP`，未填写工具 ID。 | 表单不提交，并显示 `MCP节点必须填写工具ID`。 |
| AC-010 | 意图树页面支持暗色模式且关键容器背景不透明。 | UI interaction | 浏览器根元素启用 `dark` 类并打开意图树页面。 | 树卡片、详情卡片和弹窗内容背景为非透明色，文本可读。 |
