-- 将目标模式执行型任务最低工具轮次纳入系统配置表，避免调整长任务预算时修改代码。
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
    7360,
    'chat.tool.plan_execution_min_rounds',
    '20',
    'INTEGER',
    'chat.tool',
    '目标模式执行型任务最低工具轮次',
    20,
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
