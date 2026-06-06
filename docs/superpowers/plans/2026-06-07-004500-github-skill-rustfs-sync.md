# GitHub Skill RustFS Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从 GitHub 筛选通用热门 skill，上传目录化包到 RustFS，并让 `skill` 表可重复初始化这些技能。

**Architecture:** 只做数据与对象存储同步，不改运行时 Java 业务逻辑。GitHub 下载包经根级 `SKILL.md` 校验后，以 `chat-skills/packages/<skillCode>` 目录上传到 RustFS；数据库迁移和初始化脚本写入同一批 `storage_key`、`package_size`、`package_checksum`。

**Tech Stack:** PostgreSQL `skill` 表、RustFS S3 兼容接口、目录化 Skill Runtime。

---

### Task 1: 筛选 GitHub skill

**Files:**
- Read: GitHub public repositories
- Read: `backend/src/main/resources/db/init.sql`

- [x] 使用 GitHub 搜索和 codeload zip 获取候选仓库。
- [x] 排除攻防类、高风险类、缺少明确许可或没有根级 `SKILL.md` 的候选。
- [x] 选定 `agent-skill-creator`、`humanizer`、`design-professional`、`user-research`、`web-research`，并纳入已有 `multi-search`、`web-access` 元数据修正。

### Task 2: 生成数据库种子

**Files:**
- Create: `backend/src/main/resources/db/migration/V20260607_004500__seed_popular_github_skills.sql`
- Modify: `backend/src/main/resources/db/init.sql`

- [x] 写入 7 条 GitHub skill 的可重复 upsert SQL。
- [x] 保持 `storage_key` 与 RustFS 目录前缀一致。
- [x] 同步 `package_storage_format = 'directory'`、包大小和 checksum。
- [x] 保持 `schema.sql` 不变；本仓库结构基线不承载 skill 种子数据，本次只同步迁移与 `init.sql` 初始化数据。

### Task 3: 上传 RustFS

**Files:**
- No repository file writes.

- [x] 将 5 个新增 skill 目录上传到 `codingx/chat-skills/packages/<skillCode>/`。
- [x] 保留已有 `multi-search` 与 `web-access` 对象目录，只校验并补数据库元数据。
- [x] 列举 RustFS 目录确认每个 skill 根级 `SKILL.md` 存在。

### Task 4: 验证与提交

**Files:**
- Modify: `docs/features/index.md`
- Create: `docs/features/chat/github-skill-rustfs-sync.md`

- [x] 使用 `mcp__postgres` 执行本次 SQL 并查询 7 条记录。
- [x] 运行后端编译与相关测试。
- [x] 只提交本任务文件，不纳入其他会话的 CLI 改动和构建产物。
