# Acceptance Criteria: MewCode 能力缺口治理工作台

**Spec:** `docs/superpowers/specs/2026-06-06-210242-mewcode-governed-workbench-design.md`
**Date:** 2026-06-06
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 用户端能从后端加载启用的 slash command，并选择命令提交结构化消息。 | UI interaction | 管理端启用 `/review` 命令，用户已登录聊天页。 | 输入 `/` 后出现 `/review`，选择后输入区显示命令标签，提交请求包含 `slash_command` 数据。 |
| AC-002 | 后端解析内置 slash command 时会生成命令上下文，禁用或未知命令返回中文错误。 | API | 数据库存在启用和禁用命令各一条。 | 启用命令进入发送命令上下文；禁用或未知命令通过 `ApiResponse.message` 返回“命令不可用”。 |
| AC-003 | `update_plan` 工具调用会把计划步骤写入 `chat_execution_step` 并推送 step 事件。 | Logic | 模型在本地工具循环中调用 `update_plan`，参数包含 3 个步骤。 | 仓储保存 3 条 `step_type=plan` 记录，状态分别映射为 `PENDING/RUNNING/COMPLETED`，SSE payload 包含步骤标题和顺序号。 |
| AC-004 | 命令和写入类本地工具执行前必须经过权限策略判定。 | Logic | 存在一条启用策略匹配 `rm -rf` 且动作为 `DENY`。 | 调用 `bash` 或 `shell_command` 执行 `rm -rf` 时抛出中文业务异常，并写入一条拒绝审计。 |
| AC-005 | 需要确认的策略在 MVP 中不会直接执行工具。 | Logic | 存在一条启用策略匹配 `git push` 且动作为 `CONFIRM`。 | 调用命令时返回“需要确认后再执行”的中文异常，审计结果为 `CONFIRM_REQUIRED`。 |
| AC-006 | 管理端可以查询、新增、更新、删除权限策略。 | API | 管理员已登录。 | `/api/admin/governance/permission-policies` 支持 CRUD，响应数据包含策略编码、动作、启用状态和更新时间。 |
| AC-007 | Hook 规则会在工具调用前后或任务完成时记录触发审计。 | Logic | 存在启用 Hook 规则，触发点为 `BEFORE_TOOL_CALL`。 | 工具调用前写入 Hook 审计，状态为 `SUCCESS`；规则禁用时不写入新审计。 |
| AC-008 | 管理端可以查询与维护 Hook 规则并查看最近 Hook 审计。 | API | 管理员已登录，数据库已有 Hook 规则和审计。 | 治理接口返回规则列表和最近审计，新增/编辑/删除规则后列表可刷新显示最新状态。 |
| AC-009 | 项目画像扫描只读取工作空间目录并生成技术栈、入口和验证命令摘要。 | Logic | 本地工作空间包含 `pom.xml`、`package.json` 和 `frontend/user/package.json`。 | 扫描结果包含 Java/Maven、Node/NPM 线索，验证命令包含 `mvn test` 和对应 `npm run build` 建议，不修改工作区文件。 |
| AC-010 | 管理端治理中心展示权限、Hook、项目画像、slash command 和审计入口。 | UI interaction | 管理员已登录管理端。 | 侧边栏出现“治理中心”，页面 Tabs 可切换，表格数据来自后端 API，暗色模式下文本、边框和滚动条可读。 |
| AC-011 | 数据库迁移与 `schema.sql` 同步新增治理表，所有表和字段都有中文注释。 | Logic | 执行仓库 SQL 结构检查测试。 | 迁移脚本与基线结构包含相同治理表和字段，注释内容为中文短语且末尾不加句号。 |
| AC-012 | 前端错误信息优先使用后端 `ApiResponse.message`。 | Logic | 治理接口返回失败 envelope，message 为中文。 | 管理端 API 抛出的 Error message 等于后端 message，页面展示该文案。 |
