-- 补充内置 web_access 工具，确保数据库工具表与模型可见执行器保持一致。
INSERT INTO tool (
    id,
    tool_code,
    display_name,
    description,
    category,
    source_type,
    enabled,
    sort_no,
    deleted
)
VALUES (
    9128,
    'web_access',
    '联网访问',
    '通过本机 CDP Proxy 执行联网搜索、网页抓取与浏览器交互',
    '网络访问',
    'codex-cli',
    1,
    28,
    0
)
ON CONFLICT (tool_code) DO UPDATE
SET
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    source_type = EXCLUDED.source_type,
    enabled = EXCLUDED.enabled,
    sort_no = EXCLUDED.sort_no,
    updated_at = CURRENT_TIMESTAMP,
    deleted = EXCLUDED.deleted;
