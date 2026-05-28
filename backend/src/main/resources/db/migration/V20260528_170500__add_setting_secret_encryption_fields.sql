ALTER TABLE setting
    ADD COLUMN IF NOT EXISTS encrypted_value TEXT,
    ADD COLUMN IF NOT EXISTS secret BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS masked_value VARCHAR(255),
    ADD COLUMN IF NOT EXISTS encryption_algorithm VARCHAR(64),
    ADD COLUMN IF NOT EXISTS encryption_key_version VARCHAR(64);

COMMENT ON COLUMN setting.encrypted_value IS '敏感配置密文';
COMMENT ON COLUMN setting.secret IS '是否敏感配置';
COMMENT ON COLUMN setting.masked_value IS '敏感配置脱敏值';
COMMENT ON COLUMN setting.encryption_algorithm IS '密文加密算法';
COMMENT ON COLUMN setting.encryption_key_version IS '密钥版本';

UPDATE setting
SET secret = TRUE,
    masked_value = CASE
        WHEN CHAR_LENGTH(setting_value) <= 8 THEN LEFT(setting_value, 1) || '***' || RIGHT(setting_value, 1)
        ELSE LEFT(setting_value, 4) || '***' || RIGHT(setting_value, 4)
    END,
    encryption_algorithm = COALESCE(encryption_algorithm, 'PENDING_MIGRATION'),
    encryption_key_version = COALESCE(encryption_key_version, 'v1')
WHERE setting_key IN (
    'web_search.api_key',
    'ai.api_key',
    'ai.providers.siliconflow.api_key',
    'ai.providers.bailian.api_key',
    'ai.providers.deepseek.api_key'
);
