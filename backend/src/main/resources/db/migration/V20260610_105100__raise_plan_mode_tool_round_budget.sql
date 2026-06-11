-- 提升目标模式执行型任务预算，避免大型文件生成、验证和提交链路在普通 20 轮保护处被误阻塞。
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
    '60',
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
