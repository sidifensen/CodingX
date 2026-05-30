-- 将联网搜索从单 provider 配置扩展为可调顺序的多 provider fallback。
-- API Key 槽位只声明为敏感配置，不写入真实密钥，避免密钥进入仓库历史。
INSERT INTO setting (
    id,
    setting_key,
    setting_value,
    encrypted_value,
    secret,
    masked_value,
    encryption_algorithm,
    encryption_key_version,
    value_type,
    category_code,
    description,
    sort_no,
    restart_required,
    deleted
)
VALUES
    (7301, 'web_search.provider_order', 'tavily,serpapi,exa,duckduckgo_html,bing_html', NULL, FALSE, NULL, NULL, NULL, 'STRING', 'search', '联网搜索提供方尝试顺序', 120, FALSE, 0),
    (7302, 'web_search.failure_threshold', '2', NULL, FALSE, NULL, NULL, NULL, 'INTEGER', 'search', '联网搜索提供方连续失败熔断阈值', 130, FALSE, 0),
    (7303, 'web_search.open_duration_ms', '30000', NULL, FALSE, NULL, NULL, NULL, 'LONG', 'search', '联网搜索提供方熔断打开时长毫秒', 140, FALSE, 0),
    (7310, 'web_search.providers.tavily.base_url', 'https://api.tavily.com/search', NULL, FALSE, NULL, NULL, NULL, 'STRING', 'search.providers', 'Tavily 搜索接口地址', 10, FALSE, 0),
    (7311, 'web_search.providers.tavily.api_key', '', NULL, TRUE, '', 'AES_GCM', 'v1', 'STRING', 'search.providers', 'Tavily 搜索接口密钥', 20, FALSE, 0),
    (7320, 'web_search.providers.serpapi.base_url', 'https://serpapi.com/search.json', NULL, FALSE, NULL, NULL, NULL, 'STRING', 'search.providers', 'SerpApi 搜索接口地址', 30, FALSE, 0),
    (7321, 'web_search.providers.serpapi.api_key', '', NULL, TRUE, '', 'AES_GCM', 'v1', 'STRING', 'search.providers', 'SerpApi 搜索接口密钥', 40, FALSE, 0),
    (7330, 'web_search.providers.exa.base_url', 'https://api.exa.ai/search', NULL, FALSE, NULL, NULL, NULL, 'STRING', 'search.providers', 'Exa 搜索接口地址', 50, FALSE, 0),
    (7331, 'web_search.providers.exa.api_key', '', NULL, TRUE, '', 'AES_GCM', 'v1', 'STRING', 'search.providers', 'Exa 搜索接口密钥', 60, FALSE, 0),
    (7340, 'web_search.providers.bing_html.base_url', 'https://www.bing.com/search', NULL, FALSE, NULL, NULL, NULL, 'STRING', 'search.providers', 'Bing HTML 搜索地址', 70, FALSE, 0),
    (7350, 'web_search.providers.duckduckgo_html.base_url', 'https://duckduckgo.com/html/', NULL, FALSE, NULL, NULL, NULL, 'STRING', 'search.providers', 'DuckDuckGo HTML 搜索地址', 80, FALSE, 0)
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
