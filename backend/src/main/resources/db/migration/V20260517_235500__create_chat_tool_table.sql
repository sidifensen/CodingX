CREATE TABLE IF NOT EXISTS chat_tool (
    id BIGINT PRIMARY KEY,
    tool_code VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(128),
    source_type VARCHAR(64) NOT NULL DEFAULT 'codex-cli',
    enabled SMALLINT NOT NULL DEFAULT 1,
    sort_no INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE chat_tool IS '聊天工具配置表';
COMMENT ON COLUMN chat_tool.id IS '工具主键ID';
COMMENT ON COLUMN chat_tool.tool_code IS '工具编码';
COMMENT ON COLUMN chat_tool.display_name IS '工具名称';
COMMENT ON COLUMN chat_tool.description IS '工具描述';
COMMENT ON COLUMN chat_tool.category IS '工具分类';
COMMENT ON COLUMN chat_tool.source_type IS '工具来源';
COMMENT ON COLUMN chat_tool.enabled IS '是否启用 1启用 0禁用';
COMMENT ON COLUMN chat_tool.sort_no IS '排序字段';
COMMENT ON COLUMN chat_tool.created_at IS '创建时间';
COMMENT ON COLUMN chat_tool.updated_at IS '更新时间';
COMMENT ON COLUMN chat_tool.deleted IS '是否删除 0正常 1删除';

CREATE INDEX IF NOT EXISTS idx_chat_tool_enabled_sort ON chat_tool (enabled, sort_no ASC);
