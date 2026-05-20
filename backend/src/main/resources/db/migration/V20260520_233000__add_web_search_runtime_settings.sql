INSERT INTO setting (id, setting_key, setting_value, value_type, category_code, description, sort_no, restart_required, deleted)
VALUES
    (7043, 'web_search.enabled', 'false', 'BOOLEAN', 'search', '是否启用真实联网搜索', 50, FALSE, 0),
    (7044, 'web_search.provider', 'serper', 'STRING', 'search', '联网搜索提供方编码', 60, FALSE, 0),
    (7045, 'web_search.base_url', 'https://google.serper.dev/search', 'STRING', 'search', '联网搜索接口地址', 70, FALSE, 0),
    (7046, 'web_search.api_key', '', 'STRING', 'search', '联网搜索接口密钥', 80, FALSE, 0),
    (7047, 'web_search.max_results', '5', 'INTEGER', 'search', '联网搜索单次最大候选数', 90, FALSE, 0),
    (7048, 'web_search.language', 'zh-cn', 'STRING', 'search', '联网搜索语言代码', 100, FALSE, 0),
    (7049, 'web_search.country', 'cn', 'STRING', 'search', '联网搜索地区代码', 110, FALSE, 0)
ON CONFLICT (setting_key) DO UPDATE
SET
    setting_value = EXCLUDED.setting_value,
    value_type = EXCLUDED.value_type,
    category_code = EXCLUDED.category_code,
    description = EXCLUDED.description,
    sort_no = EXCLUDED.sort_no,
    restart_required = EXCLUDED.restart_required,
    updated_at = CURRENT_TIMESTAMP,
    deleted = EXCLUDED.deleted;
