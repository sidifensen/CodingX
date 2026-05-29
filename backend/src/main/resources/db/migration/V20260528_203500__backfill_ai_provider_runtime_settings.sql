-- 为历史数据库补齐 AI 运行时 provider 配置，避免只迁移了模型 ID 却缺少真实 provider 地址和密钥键位。
INSERT INTO setting (
    id,
    setting_key,
    setting_value,
    encrypted_value,
    secret,
    masked_value,
    encryption_algorithm,
    encryption_key_version,
    value_type,
    category_code,
    description,
    sort_no,
    restart_required,
    deleted
)
VALUES
    (7080, 'ai.provider', 'siliconflow', NULL, FALSE, NULL, NULL, NULL, 'STRING', 'ai', 'AI 默认提供商编码', 60, FALSE, 0),
    (7081, 'ai.base_url', 'https://api.siliconflow.cn', NULL, FALSE, NULL, NULL, NULL, 'STRING', 'ai', 'AI 默认接口地址', 70, FALSE, 0),
    (7082, 'ai.api_key', '', NULL, TRUE, '', 'AES_GCM', 'v1', 'STRING', 'ai', 'AI 默认接口密钥', 80, FALSE, 0),
    (7083, 'ai.chat_model', 'deepseek-ai/DeepSeek-V4-Flash', NULL, FALSE, NULL, NULL, NULL, 'STRING', 'ai', 'AI 默认聊天模型名称', 90, FALSE, 0),
    (7084, 'ai.connect_timeout_ms', '10000', NULL, FALSE, NULL, NULL, NULL, 'INTEGER', 'ai', 'AI 连接超时毫秒', 100, FALSE, 0),
    (7085, 'ai.read_timeout_ms', '120000', NULL, FALSE, NULL, NULL, NULL, 'INTEGER', 'ai', 'AI 读取超时毫秒', 110, FALSE, 0),
    (7087, 'ai.providers.siliconflow.base_url', 'https://api.siliconflow.cn', NULL, FALSE, NULL, NULL, NULL, 'STRING', 'ai.providers', '硅基流动接口地址', 130, FALSE, 0),
    (7088, 'ai.providers.siliconflow.api_key', '', NULL, TRUE, '', 'AES_GCM', 'v1', 'STRING', 'ai.providers', '硅基流动接口密钥', 140, FALSE, 0),
    (7089, 'ai.providers.bailian.base_url', 'https://dashscope.aliyuncs.com', NULL, FALSE, NULL, NULL, NULL, 'STRING', 'ai.providers', '百炼接口地址', 150, FALSE, 0),
    (7090, 'ai.providers.bailian.api_key', '', NULL, TRUE, '', 'AES_GCM', 'v1', 'STRING', 'ai.providers', '百炼接口密钥', 160, FALSE, 0),
    (7091, 'ai.providers.deepseek.base_url', 'https://api.deepseek.com/v1', NULL, FALSE, NULL, NULL, NULL, 'STRING', 'ai.providers', 'DeepSeek 接口地址', 170, FALSE, 0),
    (7092, 'ai.providers.deepseek.api_key', '', NULL, TRUE, '', 'AES_GCM', 'v1', 'STRING', 'ai.providers', 'DeepSeek 接口密钥', 180, FALSE, 0)
ON CONFLICT (setting_key) DO UPDATE
SET value_type = EXCLUDED.value_type,
    category_code = EXCLUDED.category_code,
    description = EXCLUDED.description,
    sort_no = EXCLUDED.sort_no,
    restart_required = EXCLUDED.restart_required,
    secret = EXCLUDED.secret,
    masked_value = CASE
        WHEN setting.masked_value IS NOT NULL THEN setting.masked_value
        WHEN EXCLUDED.secret AND CHAR_LENGTH(setting.setting_value) > 0 THEN
            CASE
                WHEN CHAR_LENGTH(setting.setting_value) <= 8 THEN LEFT(setting.setting_value, 1) || '***' || RIGHT(setting.setting_value, 1)
                ELSE LEFT(setting.setting_value, 4) || '***' || RIGHT(setting.setting_value, 4)
            END
        ELSE EXCLUDED.masked_value
    END,
    encryption_algorithm = COALESCE(setting.encryption_algorithm, EXCLUDED.encryption_algorithm),
    encryption_key_version = COALESCE(setting.encryption_key_version, EXCLUDED.encryption_key_version),
    updated_at = CURRENT_TIMESTAMP,
    deleted = EXCLUDED.deleted;
