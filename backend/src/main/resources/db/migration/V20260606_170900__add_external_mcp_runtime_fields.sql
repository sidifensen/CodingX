ALTER TABLE IF EXISTS mcp
    ADD COLUMN IF NOT EXISTS transport_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS command TEXT,
    ADD COLUMN IF NOT EXISTS args_json TEXT,
    ADD COLUMN IF NOT EXISTS env_json TEXT,
    ADD COLUMN IF NOT EXISTS endpoint_url TEXT,
    ADD COLUMN IF NOT EXISTS headers_json TEXT,
    ADD COLUMN IF NOT EXISTS tool_schema_json TEXT,
    ADD COLUMN IF NOT EXISTS health_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS last_connected_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_error_message TEXT;

COMMENT ON COLUMN mcp.transport_type IS 'MCP传输类型';
COMMENT ON COLUMN mcp.command IS 'MCP启动命令';
COMMENT ON COLUMN mcp.args_json IS 'MCP命令参数JSON';
COMMENT ON COLUMN mcp.env_json IS 'MCP环境变量JSON';
COMMENT ON COLUMN mcp.endpoint_url IS 'MCP远程端点地址';
COMMENT ON COLUMN mcp.headers_json IS 'MCP请求头JSON';
COMMENT ON COLUMN mcp.tool_schema_json IS 'MCP工具Schema快照';
COMMENT ON COLUMN mcp.health_status IS 'MCP健康状态';
COMMENT ON COLUMN mcp.last_connected_at IS 'MCP最近连接时间';
COMMENT ON COLUMN mcp.last_error_message IS 'MCP最近错误信息';
