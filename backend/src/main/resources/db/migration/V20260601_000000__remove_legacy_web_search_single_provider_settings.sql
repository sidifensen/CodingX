-- 联网搜索已切换为 provider 链路配置，旧单 provider 配置不再展示或参与运行时。
UPDATE setting
SET deleted = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE setting_key IN (
    'web_search.provider',
    'web_search.base_url',
    'web_search.api_key'
);
