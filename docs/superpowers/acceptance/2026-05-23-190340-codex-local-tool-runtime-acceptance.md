# Acceptance Criteria: Codex Local Tool Runtime

**Spec:** `docs/superpowers/specs/2026-05-23-190340-codex-local-tool-runtime-design.md`
**Date:** 2026-05-23
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 工具 schema 只暴露已注册 Java 执行器且数据库启用的工具。 | Logic | 构造启用的 `shell_command`、未启用的 `spawn_agent` 和缺执行器的 `fake_tool`。 | schema 列表包含 `shell_command`，不包含 `spawn_agent` 与 `fake_tool`。 |
| AC-002 | `shell_command` 在绑定 workspace 中执行并回显实际目录。 | Logic | 使用临时目录绑定 `ChatToolExecutionContext`，执行创建文件命令。 | 临时目录内出现目标文件，结果元数据 `workingDirectory` 等于临时目录。 |
| AC-003 | `apply_patch` 能真实修改绑定 workspace 内文件并返回 diff 预览。 | Logic | 临时 git workspace 中已有 `README.md`。 | 文件内容被更新，结果元数据 `applied=true` 且 diff 预览包含新内容。 |
| AC-004 | OpenAI 兼容 SSE 中的 tool call 增量能被解析为工具名和 JSON 参数。 | Logic | 构造包含 `tool_calls` delta 的 SSE 文本。 | 解析器产出 `toolCode=shell_command` 且 arguments 包含 `command`。 |
| AC-005 | 聊天主流程收到模型 tool call 后会执行工具并把结果追加给下一轮模型。 | Logic | 测试 AI 客户端第一轮返回 `test_sync_tool` 调用，第二轮返回最终文本。 | `ChatToolExecutionService` 被调用一次，最终助手消息为第二轮文本。 |
| AC-006 | 没有真实 Java 后端的 Codex 占位工具不再返回假成功。 | Logic | 调用 `spawn_agent` 或 `request_plugin_install`。 | 返回明确不可用错误或健康视图标记不可用，不创建假完成结果。 |
| AC-007 | 工具种子数据描述可区分本地可执行工具和未适配工具。 | API | 执行迁移或读取迁移脚本。 | 本地可执行工具启用，占位工具禁用或文案明确“暂未适配”。 |
| AC-008 | 完整验证命令覆盖后端编译和单元测试。 | API | 完成实现后在 `backend` 目录执行验证。 | `mvn compile` 与相关 `mvn test` 命令退出码为 0。 |
