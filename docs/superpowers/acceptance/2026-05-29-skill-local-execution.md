# Acceptance Criteria: Skill 文件本地化与环境变量注入

**Spec:** `docs/superpowers/specs/2026-05-29-skill-local-execution-design.md`
**Date:** 2026-05-29
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 本地运行时单个 skill 环境变量注入 | Logic | 本地运行时，前端传入 `skillPaths: {"web-access": "/path/to/skill"}`，选择单个 skill | `ProcessBuilder.environment()` 包含 `CLAUDE_SKILL_DIR=/path/to/skill` |
| AC-002 | 本地运行时多个 skill 环境变量注入 | Logic | 本地运行时，前端传入 `skillPaths: {"web-access": "/path/a", "skill-creator": "/path/b"}`，选择两个 skill | `ProcessBuilder.environment()` 包含 `CLAUDE_SKILL_DIR_WEB_ACCESS=/path/a` 和 `CLAUDE_SKILL_DIR_SKILL_CREATOR=/path/b` |
| AC-003 | 云端运行时自动下载 skill 文件 | Logic | 云端运行时，选择 `web-access` skill，RustFS 存储键为 `chat-skills/packages/web-access` | `SkillLocalCacheService.downloadSkillToTemp()` 创建临时目录 `<tmpdir>/codingx-skills-<uuid>/web-access/`，并下载所有文件 |
| AC-004 | 云端运行时临时目录清理 | Logic | 云端运行时，响应流执行完成 | `finally` 块删除临时目录，`Files.exists(tempDir)` 返回 `false` |
| AC-005 | ChatToolExecutionContext 绑定 skill 目录 | Logic | 调用 `ChatToolExecutionContext.bindSkillDirectories(Map.of("web-access", path))` | `ChatToolExecutionContext.currentSkillDirectories()` 返回包含 `"web-access" -> path` 的 Map |
| AC-006 | ChatToolExecutionContext 清理 | Logic | 调用 `ChatToolExecutionContext.clear()` | `currentSkillDirectories()` 返回空 Map，`currentToolWorkingDirectory()` 返回 `Optional.empty()` |
| AC-007 | 环境变量名转换规则 | Logic | skill 编码为 `web-access` | 环境变量名转换为 `CLAUDE_SKILL_DIR_WEB_ACCESS`（大写，`-` 替换为 `_`） |
| AC-008 | 未选择 skill 时不注入环境变量 | Logic | 未选择任何 skill，`currentSkillDirectories()` 返回空 Map | `ProcessBuilder.environment()` 不包含任何 `CLAUDE_SKILL_DIR` 相关变量 |
| AC-009 | RustFS 下载失败时跳过该 skill | Logic | 云端运行时，RustFS 下载抛出异常 | 记录错误日志，该 skill 不加入 `skillDirs` Map，不阻塞其他 skill |
| AC-010 | 本地路径无效时跳过该 skill | Logic | 本地运行时，前端传入不存在的路径 | 验证路径不存在，该 skill 不加入 `skillDirs` Map，不阻塞其他 skill |
| AC-011 | executeExecCommand 后台命令会话注入环境变量 | Logic | 调用 `executeExecCommand()` 启动后台命令，上下文包含 skill 目录 | 后台进程的 `ProcessBuilder.environment()` 包含正确的 `CLAUDE_SKILL_DIR` 变量 |
| AC-012 | 前端传递 skillPaths 参数 | API | 本地运行时，前端发送消息携带 `skillPaths: {"web-access": "C:\\Users\\x\\.codingx\\skills\\web-access"}` | 后端 `ChatStreamController` 接收到 `skillPaths` 参数，传递给 `ChatStreamExecutionService` |
| AC-013 | 云端运行时前端不传 skillPaths | API | 云端运行时，前端发送消息不携带 `skillPaths` 或传空对象 | 后端自动从 RustFS 下载 skill 文件到临时目录 |
| AC-014 | Shell 命令执行时环境变量可用 | API | 本地运行时，选择 `web-access` skill，模型输出 `echo $env:CLAUDE_SKILL_DIR`（Windows）或 `echo $CLAUDE_SKILL_DIR`（Linux） | 命令输出为 skill 本地路径，非空字符串 |
| AC-015 | 实际 skill 脚本执行成功 | API | 本地运行时，选择 `web-access` skill，模型输出 `node "${CLAUDE_SKILL_DIR}/scripts/check-deps.mjs"` | 命令成功执行，返回 exit code 0，输出包含依赖检查结果 |
| AC-016 | 临时目录使用随机 UUID | Logic | 云端运行时，连续两次下载同一 skill | 两次创建的临时目录路径不同，包含不同的 UUID |
| AC-017 | 本地路径安全验证 | Logic | 本地运行时，前端传入路径 `/etc/passwd` 或 `C:\Windows\System32` | 验证失败，路径不在用户 home 目录的 `.codingx/skills/` 下，跳过该 skill |
| AC-018 | 向后兼容：未传 skillPaths 时云端自动下载 | API | 云端运行时，前端未传 `skillPaths` 参数（旧版前端） | 后端检测到 `skillPaths` 为 null 或空，自动从 RustFS 下载 |
| AC-019 | 不影响未选择 skill 的消息 | API | 发送消息不选择任何 skill | 消息正常处理，shell 命令正常执行，不注入 `CLAUDE_SKILL_DIR` 变量 |
| AC-020 | 不影响现有 SKILL.md 上下文注入 | API | 选择 `web-access` skill 发送消息 | `ChatSkillContextService.buildSkillContext()` 仍然正常读取 `SKILL.md` 并注入模型上下文 |
