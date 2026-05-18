-- 将 chat 前缀的配置表一次性切换为独立领域表名
ALTER TABLE IF EXISTS chat_mcp RENAME TO mcp;
ALTER TABLE IF EXISTS chat_tool RENAME TO tool;
ALTER TABLE IF EXISTS chat_skill RENAME TO skill;

-- 同步索引命名，保持语义与新表名一致
ALTER INDEX IF EXISTS idx_chat_mcp_enabled_sort RENAME TO idx_mcp_enabled_sort;
ALTER INDEX IF EXISTS idx_chat_tool_enabled_sort RENAME TO idx_tool_enabled_sort;
ALTER INDEX IF EXISTS idx_chat_skill_enabled_sort RENAME TO idx_skill_enabled_sort;
ALTER INDEX IF EXISTS idx_chat_skill_uploaded_at RENAME TO idx_skill_uploaded_at;

-- 表注释切换为去前缀后的领域命名
COMMENT ON TABLE mcp IS 'MCP配置表';
COMMENT ON TABLE tool IS '工具配置表';
COMMENT ON TABLE skill IS '技能配置表';
