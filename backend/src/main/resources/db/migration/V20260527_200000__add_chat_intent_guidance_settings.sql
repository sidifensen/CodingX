-- 新增聊天歧义引导运行时配置，默认值与 ragent guidance 配置保持一致。
INSERT INTO setting (id, setting_key, setting_value, value_type, category_code, description, sort_no, restart_required, deleted)
VALUES
    (7069, 'chat.intent.guidance.enabled', 'true', 'BOOLEAN', 'chat.intent.guidance', '是否启用聊天歧义引导', 10, FALSE, 0),
    (7070, 'chat.intent.guidance.ambiguity_score_ratio', '0.8', 'DECIMAL', 'chat.intent.guidance', '歧义引导分数比值阈值', 20, FALSE, 0),
    (7071, 'chat.intent.guidance.ambiguity_margin', '0.15', 'DECIMAL', 'chat.intent.guidance', '歧义引导边界缓冲宽度', 30, FALSE, 0),
    (7072, 'chat.intent.guidance.max_options', '6', 'INTEGER', 'chat.intent.guidance', '歧义引导最大候选数量', 40, FALSE, 0)
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
