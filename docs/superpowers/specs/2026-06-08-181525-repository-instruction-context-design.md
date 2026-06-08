# Repository Instruction Context Design

**Date:** 2026-06-08
**Status:** Approved

## 目标

删除当前“项目画像”功能及其表、接口和前端展示，改为在聊天上下文构建时读取仓库内已有的 AI 规范文件。规范文件只作为运行时上下文，不写回仓库，不生成无意义摘要。

## 参考结论

- `D:\code\codex` 以 `AGENTS.md` 为主协议，支持 `AGENTS.override.md`，按项目根到当前目录的顺序加载，并把内容作为用户上下文注入。
- `D:\code\claude-code` 以 `CLAUDE.md` 为运行时入口，支持 `.claude/CLAUDE.md`、`.claude/rules/**/*.md` 和路径级规则。
- CodingX 本次采用兼容策略：不绑定单一厂商，读取市面常见规则文件；但排除用户明确不要的 Copilot、superpowers memory 和 `.codingx` 规则。

## 支持的规范文件

运行时发现以下文件：

- Codex / 通用：`AGENTS.override.md`、`AGENTS.md`
- Claude：`CLAUDE.local.md`、`CLAUDE.md`、`.claude/CLAUDE.md`、`.claude/rules/**/*.md`
- Gemini：`GEMINI.md`
- Cursor：`.cursor/rules/**/*.mdc`、`.cursorrules`
- Windsurf：`.windsurf/rules/**/*.md`、`.windsurfrules`
- Cline / Roo：`.clinerules`、`.cline/rules/**/*.md`、`.roorules`、`.roo/rules/**/*.md`、`.roo/rules-*/**/*.md`
- Continue：`.continue/rules/**/*.md`
- JetBrains Junie：`.junie/guidelines.md`
- OpenHands：`.openhands/microagents/**/*.md`
- Kiro：`.kiro/steering/**/*.md`
- Aider：`CONVENTIONS.md`
- Qwen Code：`QWEN.md`

运行时明确排除：

- `.github/copilot-instructions.md`
- `docs/superpowers/memory/**`
- `.codingx/context.md`
- `.codingx/rules.md`

## 架构

新增 `RepositoryInstructionContextService`，职责是根据 `workspaceId` 查询本地 workspace 的 `working_directory`，在该目录内只读发现规范文件，读取 UTF-8 文本并生成可注入模型的上下文片段。它不依赖 `ChatWorkspaceBindingService`，避免聊天绑定服务与治理上下文服务形成循环依赖。

`GovernanceAgentContextService` 不再读取项目画像，而是组合“仓库规范文件”和 ACTIVE 长期记忆。聊天主流程继续只调用 `buildAgentContext(userId, workspaceId, query)`，没有仓库目录、没有规范文件或读取失败时返回空规范段，不阻断聊天。

## 数据流

1. 用户在本地 workspace 会话中发送消息，聊天服务带着 `userId`、`workspaceId` 和用户问题调用 `GovernanceAgentContextService.buildAgentContext`。
2. `RepositoryInstructionContextService` 通过 `WorkspaceMapper` 查询当前用户拥有且未删除的 workspace，读取 `working_directory` 并校验目录存在。
3. 服务按固定候选顺序发现规范文件，跳过目录、空文件、超出工作区的路径和显式排除路径；读取成功后记录来源类型、相对路径、内容长度和开头预览。
4. 规范内容按单文件与总长度上限裁剪后拼成 `# 仓库规范文件` 片段，长期记忆仍按当前用户、workspace 和问题检索。
5. `ChatApplicationService` 将治理上下文插入模型历史；若规范缺失或读取失败，只保留长期记忆或返回空字符串。

## 删除范围

- 删除 `ProjectProfileService`、项目画像领域模型、仓储、DO、Mapper 和相关测试。
- 删除管理端 `/api/admin/governance/project-profiles` 和 `/scan` 接口。
- 删除用户端绑定响应里的 `projectProfile` 字段和聊天页项目画像展示。
- 删除管理端治理中心“项目画像”页签、扫描按钮、扫描弹窗和对应 API。
- 新增迁移脚本删除 `governance_project_profile`，并从 `schema.sql` 基线中移除该表和索引。

## 日志

后端发现流程需要输出中文日志：

- 开始识别：`开始识别仓库规范文件: userId={}, workspaceId={}, path={}`
- 命中文件：`识别到仓库规范文件: workspaceId={}, sourceType={}, path={}, bytes={}, preview={}`
- 未命中：`未识别到仓库规范文件: workspaceId={}, path={}`
- 读取失败：`读取仓库规范文件失败: workspaceId={}, path={}, message={}`
- 截断：`仓库规范文件内容已截断: workspaceId={}, path={}, maxChars={}`

预览只打印开头短文本，压缩空白并限制长度，避免把完整规则或敏感内容写入日志。

## 验证

后端单元测试覆盖文件发现、排除规则、上下文拼接、长期记忆组合、绑定响应删除画像字段和 schema 删除画像表。前端测试覆盖用户端绑定结果不再包含画像、工作区智能条只展示长期记忆，以及管理端不再调用画像接口。最终按改动面执行后端 `mvn compile`、`mvn test`，用户端和管理端 `npm run build`、`npm run test:run`，并对前端改动做浏览器验证。
