ALTER TABLE IF EXISTS chat_skill RENAME TO chat_mcp;
ALTER TABLE IF EXISTS task_skill RENAME TO task_mcp;

ALTER TABLE IF EXISTS chat_mcp RENAME COLUMN skill_code TO mcp_code;
ALTER TABLE IF EXISTS task_mcp RENAME COLUMN skill_code TO mcp_code;

ALTER INDEX IF EXISTS idx_chat_skill_enabled_sort RENAME TO idx_chat_mcp_enabled_sort;
ALTER INDEX IF EXISTS idx_task_skill_task RENAME TO idx_task_mcp_task;
ALTER INDEX IF EXISTS idx_task_skill_skill_code RENAME TO idx_task_mcp_mcp_code;

COMMENT ON TABLE chat_mcp IS '聊天MCP配置表';
COMMENT ON COLUMN chat_mcp.id IS 'MCP主键ID';
COMMENT ON COLUMN chat_mcp.mcp_code IS 'MCP编码';
COMMENT ON COLUMN chat_mcp.display_name IS 'MCP名称';
COMMENT ON COLUMN chat_mcp.description IS 'MCP描述';
COMMENT ON COLUMN chat_mcp.category IS 'MCP分类';
COMMENT ON COLUMN chat_mcp.source_type IS 'MCP来源';
COMMENT ON COLUMN chat_mcp.enabled IS '是否启用 1启用 0禁用';
COMMENT ON COLUMN chat_mcp.sort_no IS '排序字段';
COMMENT ON COLUMN chat_mcp.created_at IS '创建时间';
COMMENT ON COLUMN chat_mcp.updated_at IS '更新时间';
COMMENT ON COLUMN chat_mcp.deleted IS '是否删除 0正常 1删除';

COMMENT ON TABLE task_mcp IS '任务MCP绑定表';
COMMENT ON COLUMN task_mcp.id IS '主键ID';
COMMENT ON COLUMN task_mcp.task_id IS '任务ID';
COMMENT ON COLUMN task_mcp.mcp_code IS 'MCP编码';
COMMENT ON COLUMN task_mcp.created_at IS '创建时间';
