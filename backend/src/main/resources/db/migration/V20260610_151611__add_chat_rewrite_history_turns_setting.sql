-- 新增聊天改写历史上下文轮次配置，控制短指代改写读取最近多少轮对话。
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
    7073,
    'chat.rewrite.history_turns',
    '3',
    'INTEGER',
    'chat.rewrite',
    '聊天改写历史上下文轮次',
    10,
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
