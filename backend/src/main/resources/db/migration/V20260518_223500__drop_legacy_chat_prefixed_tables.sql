-- 清理历史 chat 前缀配置表，避免升级后误读旧表。
DROP TABLE IF EXISTS chat_tool;
DROP TABLE IF EXISTS chat_skill;
DROP TABLE IF EXISTS chat_mcp;

-- 清理历史 chat 前缀索引名，兼容可能存在的遗留对象。
DROP INDEX IF EXISTS idx_chat_tool_enabled_sort;
DROP INDEX IF EXISTS idx_chat_skill_enabled_sort;
DROP INDEX IF EXISTS idx_chat_skill_uploaded_at;
DROP INDEX IF EXISTS idx_chat_mcp_enabled_sort;
