-- 新增本地工具调用轮次上限配置，默认放宽到 10 轮，避免模型在工具回灌链路中过早触顶。
INSERT INTO setting (id, setting_key, setting_value, value_type, category_code, description, sort_no, restart_required, deleted)
VALUES
    (7068, 'chat.tool.max_rounds', '10', 'INTEGER', 'chat.tool', '本地工具调用最大轮次', 10, FALSE, 0)
ON CONFLICT (setting_key) DO UPDATE
SET setting_value = EXCLUDED.setting_value,
    value_type = EXCLUDED.value_type,
    category_code = EXCLUDED.category_code,
    description = EXCLUDED.description,
    sort_no = EXCLUDED.sort_no,
    restart_required = EXCLUDED.restart_required,
    updated_at = CURRENT_TIMESTAMP,
    deleted = 0;
