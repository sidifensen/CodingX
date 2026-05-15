ALTER TABLE chat_intent_node
    ADD COLUMN IF NOT EXISTS mcp_tool_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS param_prompt_template TEXT;

COMMENT ON COLUMN chat_intent_node.mcp_tool_id IS 'MCP 工具标识';
COMMENT ON COLUMN chat_intent_node.param_prompt_template IS 'MCP 参数提取提示词模板';

UPDATE chat_intent_node
SET enabled = 1
WHERE intent_code = 'sales'
  AND deleted = 0;

UPDATE chat_intent_node
SET enabled = 1,
    mcp_tool_id = 'sales_query',
    param_prompt_template = '请提取销售统计查询参数'
WHERE intent_code = 'sales-data'
  AND deleted = 0;
