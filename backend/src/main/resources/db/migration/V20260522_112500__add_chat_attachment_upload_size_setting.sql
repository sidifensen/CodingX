-- 新增聊天附件上传大小配置，统一由 setting 表托管，默认值为 10MB。
INSERT INTO setting (id, setting_key, setting_value, value_type, category_code, description, sort_no, restart_required, deleted)
VALUES
    (7067, 'chat.attachment.max_file_size_bytes', '10485760', 'LONG', 'chat.attachment', '聊天附件上传单文件最大字节数', 10, FALSE, 0)
ON CONFLICT (setting_key) DO UPDATE
SET setting_value = EXCLUDED.setting_value,
    value_type = EXCLUDED.value_type,
    category_code = EXCLUDED.category_code,
    description = EXCLUDED.description,
    sort_no = EXCLUDED.sort_no,
    restart_required = EXCLUDED.restart_required,
    updated_at = CURRENT_TIMESTAMP,
    deleted = 0;
