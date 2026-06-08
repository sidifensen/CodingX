# Acceptance Criteria: Repository Instruction Context

**Spec:** `docs/superpowers/specs/2026-06-08-181525-repository-instruction-context-design.md`
**Date:** 2026-06-08
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 本地 workspace 存在 `AGENTS.md`、`CLAUDE.md` 和 `.cursor/rules/*.mdc` 时，后端上下文会包含这些规范文件内容。 | Logic | 单元测试创建临时仓库和 workspace 记录。 | `RepositoryInstructionContextService.buildInstructionContext` 返回 `# 仓库规范文件`，并包含三个文件的相对路径和正文片段。 |
| AC-002 | 后端不会加载用户明确排除的 Copilot、superpowers memory 和 `.codingx` 文件。 | Logic | 临时仓库同时创建 `.github/copilot-instructions.md`、`docs/superpowers/memory/a.md`、`.codingx/context.md`、`.codingx/rules.md`。 | 返回上下文不包含这些文件名和文件正文。 |
| AC-003 | 后端识别规范文件时会打印发现流程和短预览。 | Logic | 单元测试通过输出捕获执行一次规范文件发现。 | 日志包含“开始识别仓库规范文件”和“识别到仓库规范文件”，包含相对路径和压缩后的开头预览，不包含完整超长正文。 |
| AC-004 | `GovernanceAgentContextService` 组合仓库规范文件和 ACTIVE 长期记忆，不再读取项目画像。 | Logic | Mock 规范服务返回规则上下文，Mock 长期记忆服务返回一条 ACTIVE 记忆。 | 构建结果包含“仓库规范文件”和“长期记忆”，不包含“项目画像”。 |
| AC-005 | 用户绑定本地仓库目录后，后端响应只返回 workspace 信息和已生效记忆数量。 | API | 控制器单元测试 mock `ChatWorkspaceBindingService.WorkspaceBindingResult`。 | `/api/chat/workspace/bind-repository` 响应含 `repositoryPath`、`workspaceId`、`workspaceName`、`activeMemoryCount`，不含 `projectProfile`。 |
| AC-006 | 管理端治理接口不再暴露项目画像列表和扫描接口。 | API | 后端控制器代码和前端 API 测试更新后运行。 | `AdminGovernanceController` 没有 `/project-profiles` 映射，`AdminChatApi` 没有 `listProjectProfiles` 和 `scanProjectProfile`。 |
| AC-007 | 数据库基线和新迁移都会移除项目画像表。 | Logic | 读取迁移脚本和 `backend/src/main/resources/db/schema.sql`。 | 新迁移包含 `DROP TABLE IF EXISTS governance_project_profile`，`schema.sql` 不包含 `CREATE TABLE IF NOT EXISTS governance_project_profile` 或画像索引。 |
| AC-008 | 用户端聊天页不再展示项目画像摘要，但仍能展示已生效长期记忆。 | UI interaction | 前端测试渲染带 ACTIVE 记忆的工作区状态。 | 页面出现“工作区记忆”与记忆内容，不出现“项目画像摘要”或项目画像字段。 |
| AC-009 | 管理端治理中心不再显示“项目画像”页签和扫描入口。 | UI interaction | 管理端页面测试加载治理中心。 | 页面没有“项目画像”Tab，不调用画像 API，仍显示权限、Hook、长期记忆、Slash Command 和权限审计。 |
| AC-010 | 项目画像相关 Java 类型、Mapper、仓储和测试文件已删除，代码库不再引用 `GovernanceProjectProfile`。 | Logic | 执行 `rg "GovernanceProjectProfile|ProjectProfileService|governance_project_profile"`。 | 除历史迁移或文档说明外，生产代码和测试代码没有项目画像引用。 |
