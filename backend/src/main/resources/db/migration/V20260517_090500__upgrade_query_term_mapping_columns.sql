ALTER TABLE chat_query_term_mapping
    ADD COLUMN IF NOT EXISTS match_type INTEGER,
    ADD COLUMN IF NOT EXISTS priority INTEGER,
    ADD COLUMN IF NOT EXISTS remark VARCHAR(255);

UPDATE chat_query_term_mapping
SET
    match_type = COALESCE(match_type,
        CASE
            WHEN mapping_type = 'prefix' THEN 2
            WHEN mapping_type = 'regex' THEN 3
            WHEN mapping_type = 'word' THEN 4
            ELSE 1
        END
    ),
    priority = COALESCE(priority, sort_no, 0)
WHERE deleted = 0;

ALTER TABLE chat_query_term_mapping
    ALTER COLUMN match_type SET DEFAULT 1,
    ALTER COLUMN priority SET DEFAULT 0;

UPDATE chat_query_term_mapping
SET match_type = 1
WHERE match_type IS NULL;

UPDATE chat_query_term_mapping
SET priority = 0
WHERE priority IS NULL;

ALTER TABLE chat_query_term_mapping
    ALTER COLUMN match_type SET NOT NULL,
    ALTER COLUMN priority SET NOT NULL;

COMMENT ON TABLE chat_query_term_mapping IS '关键词归一化映射表';
COMMENT ON COLUMN chat_query_term_mapping.id IS '映射主键ID';
COMMENT ON COLUMN chat_query_term_mapping.source_term IS '原始词';
COMMENT ON COLUMN chat_query_term_mapping.target_term IS '目标词';
COMMENT ON COLUMN chat_query_term_mapping.match_type IS '匹配类型 1精确 2前缀 3正则 4整词';
COMMENT ON COLUMN chat_query_term_mapping.priority IS '优先级 数值越小越优先';
COMMENT ON COLUMN chat_query_term_mapping.enabled IS '是否启用 1启用 0禁用';
COMMENT ON COLUMN chat_query_term_mapping.remark IS '映射备注';
COMMENT ON COLUMN chat_query_term_mapping.created_at IS '创建时间';
COMMENT ON COLUMN chat_query_term_mapping.updated_at IS '更新时间';
COMMENT ON COLUMN chat_query_term_mapping.deleted IS '逻辑删除标记 0未删除 1已删除';

DROP INDEX IF EXISTS idx_chat_query_term_mapping_source;
CREATE INDEX IF NOT EXISTS idx_chat_query_term_mapping_source ON chat_query_term_mapping (source_term, enabled, priority ASC);
