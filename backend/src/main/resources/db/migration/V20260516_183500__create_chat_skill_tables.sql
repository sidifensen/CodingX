CREATE TABLE IF NOT EXISTS chat_mcp (
    id BIGINT PRIMARY KEY,
    mcp_code VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(128),
    source_type VARCHAR(64) NOT NULL DEFAULT 'built-in',
    enabled SMALLINT NOT NULL DEFAULT 1,
    sort_no INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
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

CREATE TABLE IF NOT EXISTS task_mcp (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    mcp_code VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE task_mcp IS '任务MCP绑定表';
COMMENT ON COLUMN task_mcp.id IS '主键ID';
COMMENT ON COLUMN task_mcp.task_id IS '任务ID';
COMMENT ON COLUMN task_mcp.mcp_code IS 'MCP编码';
COMMENT ON COLUMN task_mcp.created_at IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_chat_mcp_enabled_sort ON chat_mcp (enabled, sort_no ASC);
CREATE INDEX IF NOT EXISTS idx_task_mcp_task ON task_mcp (task_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_task_mcp_mcp_code ON task_mcp (mcp_code);

INSERT INTO chat_mcp (id, mcp_code, display_name, description, category, source_type, enabled, sort_no, deleted)
VALUES
    (7101, 'sales_query', '销售查询', '查询销售汇总、排名、趋势与明细', '销售', 'built-in', 1, 1, 0),
    (7102, 'ticket_query', '工单查询', '查询工单状态、列表、优先级与解决率', '工单', 'built-in', 1, 2, 0),
    (7103, 'weather_query', '天气查询', '查询当前天气与未来预报', '天气', 'built-in', 1, 3, 0)
ON CONFLICT (mcp_code) DO UPDATE
SET
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    source_type = EXCLUDED.source_type,
    enabled = EXCLUDED.enabled,
    sort_no = EXCLUDED.sort_no,
    updated_at = CURRENT_TIMESTAMP,
    deleted = EXCLUDED.deleted;
