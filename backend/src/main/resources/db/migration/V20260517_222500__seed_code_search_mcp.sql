INSERT INTO chat_mcp (id, mcp_code, display_name, description, category, source_type, enabled, sort_no, deleted)
VALUES
    (7100, 'code_search', '代码检索', '按关键词检索代码文件、行号与命中片段', '研发', 'built-in', 1, 0, 0)
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
