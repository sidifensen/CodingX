INSERT INTO setting (
    id,
    setting_key,
    setting_value,
    value_type,
    category_code,
    description,
    sort_no,
    restart_required,
    deleted
)
VALUES (
    7053,
    'ai.selection.stream_completion_timeout_ms',
    '300000',
    'LONG',
    'ai.routing',
    '模型路由整流完成超时毫秒',
    40,
    FALSE,
    0
)
ON CONFLICT (setting_key) DO UPDATE SET
    setting_value = EXCLUDED.setting_value,
    value_type = EXCLUDED.value_type,
    category_code = EXCLUDED.category_code,
    description = EXCLUDED.description,
    sort_no = EXCLUDED.sort_no,
    restart_required = EXCLUDED.restart_required,
    deleted = 0,
    updated_at = CURRENT_TIMESTAMP;
