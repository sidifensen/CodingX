# Acceptance Criteria: Remove Code Search MCP

**Spec:** `docs/superpowers/specs/2026-06-07-222030-remove-code-search-mcp-design.md`
**Date:** 2026-06-07
**Status:** Approved

---

## Criteria

| ID | Description | Test Type | Preconditions | Expected Result |
|----|-------------|-----------|---------------|-----------------|
| AC-001 | 数据库迁移会清理代码检索意图子树。 | Logic | 读取新增迁移脚本。 | 脚本递归或等价删除 `chat_intent_node` 中 `code` 与 `code-search`，且不匹配天气、系统或搜索节点。 |
| AC-002 | 数据库迁移会清理代码检索 MCP 配置。 | Logic | 读取新增迁移脚本。 | 脚本删除 `mcp.mcp_code = 'code_search'`，且不删除 `weather_query`。 |
| AC-003 | 数据库迁移会清理代码检索运行时设置。 | Logic | 读取新增迁移脚本。 | 脚本删除 `setting_key LIKE 'code_search.%'` 或 `category_code = 'code_search'` 的设置。 |
| AC-004 | 新环境初始化不再回填代码检索能力。 | Logic | 读取 `backend/src/main/resources/db/init.sql`。 | 文件不包含 `'code-search'`、`'code_search'`、`代码检索` 或 `代码查找` 种子记录。 |
| AC-005 | 后端不再注册代码检索 MCP 执行器。 | Logic | 编译后端应用。 | `CodeSearchMcpToolExecutor` 源文件不存在，且无生产代码引用该类。 |
| AC-006 | 代码检索运行时配置读取被移除。 | Logic | 编译后端应用。 | `RuntimeProperties` 和 `RuntimeSettingService` 不再暴露 `codeSearch*` 字段或方法。 |
| AC-007 | 保留 MCP 能力仍可执行。 | Logic | 运行后端 MCP 流程测试。 | 天气 MCP 流程测试通过，并验证 `weather_query` 仍会调用 `ChatMcpExecutionService`。 |
| AC-008 | 管理端不再硬编码代码检索设置分类。 | Logic | 读取并测试管理端设置页。 | `frontend/admin/src/pages/Settings.tsx` 不再包含 `code_search: '代码检索运行时'`。 |
| AC-009 | 管理端测试数据不再把代码检索作为可见 MCP 或技能。 | Logic | 运行管理端相关测试。 | `MCP.test.tsx` 与 `Skills.test.tsx` 不再使用 `code_search` / `代码检索` 作为 mock 可见项。 |
| AC-010 | 浏览器中意图树不再出现代码检索节点。 | UI interaction | 后端和管理端前端已启动并应用迁移或手动清理数据库。 | `http://localhost:5003/intent-tree` 页面不出现“代码检索与定位”“代码查找”“code-search”。 |

## Self Review

- 每条标准都有确定的文件、测试或浏览器观察方式。
- 标准覆盖数据库迁移、基线初始化、后端执行器、配置读取、管理端展示和浏览器验证。
- 未使用 TBD、TODO 或不可判定表述。
