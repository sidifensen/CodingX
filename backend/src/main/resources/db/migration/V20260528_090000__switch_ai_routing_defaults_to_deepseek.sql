-- 将历史环境里的 AI 路由默认值切换到 DeepSeek，避免已有数据库继续沿用 qwen 默认值。
INSERT INTO setting (id, setting_key, setting_value, value_type, category_code, description, sort_no, restart_required, deleted)
VALUES
    (7053, 'ai.chat.default_model', 'deepseek-chat', 'STRING', 'ai.routing', '模型路由默认模型ID', 40, FALSE, 0),
    (7054, 'ai.chat.deep_thinking_model', 'deepseek-reasoner', 'STRING', 'ai.routing', '模型路由深度思考模型ID', 50, FALSE, 0)
ON CONFLICT (setting_key) DO UPDATE
SET setting_value = EXCLUDED.setting_value,
    value_type = EXCLUDED.value_type,
    category_code = EXCLUDED.category_code,
    description = EXCLUDED.description,
    sort_no = EXCLUDED.sort_no,
    restart_required = EXCLUDED.restart_required,
    updated_at = CURRENT_TIMESTAMP,
    deleted = EXCLUDED.deleted;
