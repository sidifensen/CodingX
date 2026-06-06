# GitHub Skill RustFS 同步

## 功能用途

系统预置一批来自 GitHub 的通用 skill，并将其目录化包上传到 RustFS。用户在聊天中选择这些技能后，后端可以按 `skill.storage_key` 下载 `SKILL.md`、资源和脚本，再注入本轮模型上下文或绑定到云端执行临时目录。

## 使用入口

- 用户端聊天输入区的技能选择列表。
- 管理端技能列表和技能包资源预览。
- 后端运行时 `SkillRuntimeService` 与 `SkillLocalCacheService`。

## 核心流程

1. 运维或迁移流程先从 GitHub 下载候选 skill 目录，筛选时必须确认根级存在 `SKILL.md`，并排除攻防类、高风险类、无明确许可或不适合默认启用的仓库。通过筛选后按目录文件清单计算总大小和 checksum，checksum 规则与 `AdminChatSkillService` 的目录上传逻辑保持一致。
2. 对象存储同步阶段将新增 skill 目录上传到 `chat-skills/packages/<skillCode>/`。上传前只清理目标 skill 前缀，避免影响其他技能包；上传后通过 RustFS 列举确认每个目录都有根级 `SKILL.md`。
3. 数据库迁移使用 `skill_code` 做幂等 upsert，写入展示名、中文描述、分类、GitHub 来源 URL、`storage_key`、`package_storage_format = 'directory'`、包大小和 checksum。已有 `multi-search` 与 `web-access` 也会同步修正包元数据，避免只存在 storage key 但无法校验内容。
4. 聊天运行时收到用户显式选择的 skill 后，`SkillRuntimeService` 按 `storage_key` 读取 RustFS 目录中的 `SKILL.md` 和元数据文件。若读取失败或缺少 manifest，该技能不会进入模型上下文，避免把不可用技能暴露给用户。

## 关键文件

- `backend/src/main/resources/db/migration/V20260607_004500__seed_popular_github_skills.sql`：新增 GitHub skill 种子迁移。
- `backend/src/main/resources/db/init.sql`：新环境初始化数据基线。
- `backend/src/main/java/com/codingx/skill/application/service/SkillRuntimeService.java`：按目录化对象存储读取 skill 运行时内容。
- `backend/src/main/java/com/codingx/skill/application/service/SkillLocalCacheService.java`：云端执行前将选中 skill 下载到临时目录。

## 关键数据

本次同步包含 `multi-search`、`web-access`、`agent-skill-creator`、`humanizer`、`design-professional`、`user-research`、`web-research`。所有记录的 `source_type` 保存 GitHub 仓库 URL，`storage_key` 保存 RustFS 目录前缀，`package_storage_format` 固定为 `directory`。

## 验证方式

- 查询 `skill` 表确认 7 条 GitHub skill 均存在且 `storage_key`、`package_size`、`package_checksum` 非空。
- 列举 RustFS `chat-skills/packages/` 下对应目录，确认每个目录存在根级 `SKILL.md`。
- 运行后端编译与技能运行时相关测试，确认目录化 skill 读取逻辑不回归。
