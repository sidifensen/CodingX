CREATE TABLE IF NOT EXISTS chat_runtime_setting (
    id BIGINT PRIMARY KEY,
    setting_key VARCHAR(128) NOT NULL UNIQUE,
    setting_value TEXT NOT NULL,
    value_type VARCHAR(32) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE chat_runtime_setting IS '聊天运行时配置表';
COMMENT ON COLUMN chat_runtime_setting.id IS '主键ID';
COMMENT ON COLUMN chat_runtime_setting.setting_key IS '配置键';
COMMENT ON COLUMN chat_runtime_setting.setting_value IS '配置值';
COMMENT ON COLUMN chat_runtime_setting.value_type IS '值类型';
COMMENT ON COLUMN chat_runtime_setting.description IS '配置说明';
COMMENT ON COLUMN chat_runtime_setting.created_at IS '创建时间';
COMMENT ON COLUMN chat_runtime_setting.updated_at IS '更新时间';
COMMENT ON COLUMN chat_runtime_setting.deleted IS '是否删除 0：正常 1：删除';

INSERT INTO chat_runtime_setting (id, setting_key, setting_value, value_type, description, deleted)
VALUES
    (7001, 'search.top_k', '5', 'INTEGER', '搜索结果返回数量上限', 0),
    (7002, 'search.rerank_enabled', 'true', 'BOOLEAN', '是否启用搜索结果重排', 0),
    (7003, 'chat.token_budget', '4096', 'INTEGER', '聊天请求 token 预算', 0),
    (7004, 'chat.thinking_visible', 'true', 'BOOLEAN', '是否展示思考内容', 0),
    (7005, 'queue.max_concurrent', '1', 'INTEGER', '聊天链路最大并发数', 0)
ON CONFLICT (setting_key) DO UPDATE
SET setting_value = EXCLUDED.setting_value,
    value_type = EXCLUDED.value_type,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP,
    deleted = 0;
