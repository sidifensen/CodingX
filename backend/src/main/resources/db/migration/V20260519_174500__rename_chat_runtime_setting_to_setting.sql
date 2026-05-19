-- 将聊天运行时配置表升级为通用系统配置表，支持分类与排序等管理能力。
ALTER TABLE IF EXISTS chat_runtime_setting RENAME TO setting;

-- 兼容已存在 setting 表但缺字段的历史环境：补齐分类、排序和重启标记字段。
ALTER TABLE setting
    ADD COLUMN IF NOT EXISTS category_code VARCHAR(64) NOT NULL DEFAULT 'general',
    ADD COLUMN IF NOT EXISTS sort_no INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS restart_required BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON TABLE setting IS '系统配置表';
COMMENT ON COLUMN setting.id IS '主键ID';
COMMENT ON COLUMN setting.setting_key IS '配置键';
COMMENT ON COLUMN setting.setting_value IS '配置值';
COMMENT ON COLUMN setting.value_type IS '值类型';
COMMENT ON COLUMN setting.category_code IS '配置分类编码';
COMMENT ON COLUMN setting.description IS '配置说明';
COMMENT ON COLUMN setting.sort_no IS '分类内排序号';
COMMENT ON COLUMN setting.restart_required IS '是否需要重启生效';
COMMENT ON COLUMN setting.created_at IS '创建时间';
COMMENT ON COLUMN setting.updated_at IS '更新时间';
COMMENT ON COLUMN setting.deleted IS '是否删除 0：正常 1：删除';

-- 旧种子键迁移到新的命名空间，确保管理端与运行时读同一套配置。
UPDATE setting
SET setting_key = 'queue.max_concurrent',
    category_code = 'queue',
    sort_no = 10
WHERE setting_key = 'queue.max_concurrent';

UPDATE setting
SET setting_key = 'search.top_k',
    category_code = 'search',
    sort_no = 10
WHERE setting_key = 'search.top_k';

UPDATE setting
SET setting_key = 'search.rerank_enabled',
    category_code = 'search',
    sort_no = 20
WHERE setting_key = 'search.rerank_enabled';

UPDATE setting
SET setting_key = 'chat.memory.summary_enabled',
    category_code = 'chat.memory',
    description = '是否启用聊天历史摘要压缩',
    sort_no = 10
WHERE setting_key = 'chat.thinking_visible';

UPDATE setting
SET setting_key = 'chat.memory.summary_trigger_messages',
    category_code = 'chat.memory',
    description = '聊天历史摘要触发消息数阈值',
    sort_no = 20
WHERE setting_key = 'chat.token_budget';

-- 补齐未覆盖的历史记录分类，避免老环境出现空分类。
UPDATE setting
SET category_code = 'general'
WHERE category_code IS NULL OR category_code = '';

-- 写入本次新增可统一管理的配置项，已存在时做幂等更新。
INSERT INTO setting (id, setting_key, setting_value, value_type, category_code, description, sort_no, restart_required, deleted)
VALUES
    (7010, 'chat.memory.history_keep_turns', '6', 'INTEGER', 'chat.memory', '聊天历史原文保留轮次', 30, FALSE, 0),
    (7011, 'chat.memory.summary_max_characters', '4000', 'INTEGER', 'chat.memory', '聊天历史摘要最大字符数', 40, FALSE, 0),
    (7020, 'search.timeout_ms', '15000', 'LONG', 'search', '单次搜索超时毫秒', 30, FALSE, 0),
    (7021, 'search.max_parallel_questions', '3', 'INTEGER', 'search', '搜索拆分子问题最大并发数', 40, FALSE, 0),
    (7030, 'queue.acquire_timeout_ms', '3000', 'LONG', 'queue', '队列获取执行资格超时毫秒', 20, TRUE, 0),
    (7031, 'queue.poll_interval_ms', '200', 'LONG', 'queue', '队列轮询间隔毫秒', 30, TRUE, 0),
    (7032, 'queue.lease_seconds', '300', 'LONG', 'queue', '执行资格租约秒数', 40, TRUE, 0),
    (7033, 'queue.lease_renew_interval_ms', '10000', 'LONG', 'queue', '执行资格续租间隔毫秒', 50, TRUE, 0),
    (7040, 'code_search.root', '', 'STRING', 'code_search', '代码检索根目录', 10, TRUE, 0),
    (7041, 'code_search.max_results', '20', 'INTEGER', 'code_search', '代码检索最大返回命中数', 20, TRUE, 0),
    (7042, 'code_search.max_file_size_bytes', '1048576', 'LONG', 'code_search', '代码检索单文件最大扫描字节数', 30, TRUE, 0),
    (7050, 'ai.selection.failure_threshold', '2', 'INTEGER', 'ai.routing', '模型路由连续失败熔断阈值', 10, TRUE, 0),
    (7051, 'ai.selection.open_duration_ms', '30000', 'LONG', 'ai.routing', '模型路由熔断打开时长毫秒', 20, TRUE, 0),
    (7052, 'ai.selection.first_packet_timeout_ms', '60000', 'LONG', 'ai.routing', '模型路由首包超时毫秒', 30, FALSE, 0),
    (7053, 'ai.chat.default_model', 'qwen-plus', 'STRING', 'ai.routing', '模型路由默认模型ID', 40, FALSE, 0),
    (7054, 'ai.chat.deep_thinking_model', 'qwen3-max', 'STRING', 'ai.routing', '模型路由深度思考模型ID', 50, FALSE, 0)
ON CONFLICT (setting_key) DO UPDATE
SET setting_value = EXCLUDED.setting_value,
    value_type = EXCLUDED.value_type,
    category_code = EXCLUDED.category_code,
    description = EXCLUDED.description,
    sort_no = EXCLUDED.sort_no,
    restart_required = EXCLUDED.restart_required,
    updated_at = CURRENT_TIMESTAMP,
    deleted = 0;

-- 统一校准重启标记，确保管理端展示与当前真实生效机制一致。
UPDATE setting
SET restart_required = FALSE
WHERE setting_key IN (
    'chat.memory.summary_enabled',
    'chat.memory.summary_trigger_messages',
    'chat.memory.history_keep_turns',
    'chat.memory.summary_max_characters',
    'search.top_k',
    'search.rerank_enabled',
    'search.timeout_ms',
    'search.max_parallel_questions',
    'ai.selection.first_packet_timeout_ms',
    'ai.chat.default_model',
    'ai.chat.deep_thinking_model'
);

UPDATE setting
SET restart_required = TRUE
WHERE setting_key IN (
    'queue.max_concurrent',
    'queue.acquire_timeout_ms',
    'queue.poll_interval_ms',
    'queue.lease_seconds',
    'queue.lease_renew_interval_ms',
    'code_search.root',
    'code_search.max_results',
    'code_search.max_file_size_bytes',
    'ai.selection.failure_threshold',
    'ai.selection.open_duration_ms'
);

-- 重建索引，统一使用 setting 表命名。
DROP INDEX IF EXISTS idx_chat_runtime_setting_key;
CREATE INDEX IF NOT EXISTS idx_setting_category_sort_deleted ON setting (category_code, sort_no, deleted);
