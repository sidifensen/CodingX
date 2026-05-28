-- 降低模型首包等待窗口，让慢 provider 更快触发候选切换，避免用户长时间无反馈。
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
VALUES
    (7052, 'ai.selection.first_packet_timeout_ms', '15000', 'LONG', 'ai.routing', '模型路由首包超时毫秒', 30, FALSE, 0)
ON CONFLICT (setting_key) DO UPDATE
SET setting_value = EXCLUDED.setting_value,
    value_type = EXCLUDED.value_type,
    category_code = EXCLUDED.category_code,
    description = EXCLUDED.description,
    sort_no = EXCLUDED.sort_no,
    restart_required = EXCLUDED.restart_required,
    updated_at = CURRENT_TIMESTAMP,
    deleted = EXCLUDED.deleted;
